/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.otel.bridge;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadableSpan;

import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.lang.Nullable;

/**
 * OpenTelemetry implementation of a {@link TraceContext}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class OtelTraceContext implements TraceContext {

	final Deque<Context> stack;

	final SpanContext delegate;

	final Span span;

	public OtelTraceContext(Deque<Context> stack, SpanContext delegate, @Nullable Span span) {
		this.stack = stack;
		this.delegate = delegate;
		this.span = span;
	}

	public OtelTraceContext(SpanContext delegate, @Nullable Span span) {
		this.stack = new ArrayDeque<>();
		this.delegate = delegate;
		this.span = span;
	}

	public OtelTraceContext(Span span) {
		this(new ArrayDeque<>(), span.getSpanContext(), span);
	}

	public OtelTraceContext(SpanFromSpanContext span) {
		this(span.otelTraceContext.stack, span.getSpanContext(), span);
	}

	public static TraceContext fromOtel(SpanContext traceContext) {
		return new OtelTraceContext(traceContext, null);
	}

	public static Context toOtelContext(TraceContext context) {
		if (context instanceof OtelTraceContext) {
			Span span = ((OtelTraceContext) context).span;
			if (span != null) {
				return span.storeInContext(Context.current());
			}
		}
		return Context.current();
	}

	public Deque<Context> stackCopy() {
		return new ArrayDeque<>(this.stack);
	}

	void addContext(Context context) {
		this.stack.addFirst(context);
	}

	@Override
	public String traceId() {
		return this.delegate.getTraceIdAsHexString();
	}

	@Override
	@Nullable
	public String parentId() {
		if (this.span instanceof ReadableSpan) {
			ReadableSpan readableSpan = (ReadableSpan) this.span;
			return readableSpan.toSpanData().getParentSpanId();
		}
		return null;
	}

	@Override
	public String spanId() {
		return this.delegate.getSpanIdAsHexString();
	}

	@Override
	public String toString() {
		return this.delegate != null ? this.delegate.toString() : "null";
	}

	@Override
	public boolean equals(Object o) {
		return Objects.equals(this.delegate, o);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.delegate);
	}

	@Nullable
	public Boolean sampled() {
		return this.delegate.isSampled();
	}

	public Span span() {
		return this.span;
	}

}
