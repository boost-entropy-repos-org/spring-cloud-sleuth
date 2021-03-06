/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.brave.instrument.web;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import brave.Span;
import brave.baggage.BaggagePropagation;
import brave.http.HttpTracing;
import brave.propagation.B3Propagation;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.GenericFilterBean;

import static brave.Span.Kind.CLIENT;
import static brave.propagation.B3Propagation.Format.SINGLE_NO_PARENT;
import static brave.propagation.B3SingleFormat.writeB3SingleFormat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(classes = TraceCustomFilterResponseInjectorTests.Config.class, webEnvironment = RANDOM_PORT)
@DirtiesContext
public class TraceCustomFilterResponseInjectorTests {

	@Autowired
	RestTemplate restTemplate;

	@Autowired
	Config config;

	@Autowired
	CustomRestController customRestController;

	@Test
	@SuppressWarnings("unchecked")
	public void should_inject_trace_and_span_ids_in_response_headers() {
		RequestEntity<?> requestEntity = RequestEntity
				.get(URI.create("http://localhost:" + this.config.port + "/headers")).build();

		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> responseEntity = this.restTemplate.exchange(requestEntity, Map.class);

		then(responseEntity.getHeaders()).containsKey("b3").as("Trace headers must be present in response headers");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class Config implements ApplicationListener<ServletWebServerInitializedEvent> {

		int port;

		@Bean
		BaggagePropagation.FactoryBuilder baggagePropagationFactoryBuilder() {
			// Use b3 single format as it is less verbose
			return BaggagePropagation.newFactoryBuilder(
					B3Propagation.newFactoryBuilder().injectFormat(CLIENT, SINGLE_NO_PARENT).build());
		}

		// tag::configuration[]
		@Bean
		HttpResponseInjectingTraceFilter responseInjectingTraceFilter(HttpTracing httpTracing) {
			return new HttpResponseInjectingTraceFilter(httpTracing);
		}
		// end::configuration[]

		@Override
		public void onApplicationEvent(ServletWebServerInitializedEvent event) {
			this.port = event.getSource().getPort();
		}

		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean
		CustomRestController customRestController() {
			return new CustomRestController();
		}

	}

	// tag::injector[]
	static class HttpResponseInjectingTraceFilter extends GenericFilterBean {

		private final HttpTracing httpTracing;

		HttpResponseInjectingTraceFilter(HttpTracing httpTracing) {
			this.httpTracing = httpTracing;
		}

		@Override
		public void doFilter(ServletRequest request, ServletResponse servletResponse, FilterChain filterChain)
				throws IOException, ServletException {
			HttpServletResponse response = (HttpServletResponse) servletResponse;
			Span currentSpan = this.httpTracing.tracing().tracer().currentSpan();
			response.addHeader("b3", writeB3SingleFormat(currentSpan.context()));
			filterChain.doFilter(request, response);
		}

	}
	// end::injector[]

	@RestController
	static class CustomRestController {

		@RequestMapping("/headers")
		public Map<String, String> headers(@RequestHeader HttpHeaders headers) {
			Map<String, String> map = new HashMap<>();
			for (String key : headers.keySet()) {
				map.put(key, headers.getFirst(key));
			}
			return map;
		}

	}

}
