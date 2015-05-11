/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.hibernate.validator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 1)
@Measurement(iterations = 10)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@SuppressWarnings("unused")
public class DateConstraintBenchmark {

	// State class to hold the test dates
	@State(Scope.Benchmark)
	public static class DateHolder {
		static List<Date> dates = new ArrayList<>(  );

		static {
			long millisPerDay = 1000 * 60 * 60 * 24;
			long time = System.currentTimeMillis();
			Date now = new Date( time );
			dates.add( now );
			for ( int i = 1; i <= 100; i++ ) {
				dates.add( new Date( time - i * millisPerDay ) );
			}
			for ( int i = 1; i <= 100; i++ ) {
				dates.add( new Date( time + i * millisPerDay ) );
			}
		}
	}

	@State(Scope.Benchmark)
	public static class CompareBySystemTime {
		public boolean isBefore(Date date) {
			return date.getTime() > System.currentTimeMillis();
		}
	}

	@State(Scope.Benchmark)
	public static class CompareByCalendar {
		public boolean isBefore(Date date) {
			Calendar calendar = Calendar.getInstance();
			return date.after( calendar.getTime() );
		}
	}

	@Benchmark
	@Fork(1)
	@OperationsPerInvocation(201)
	public void compareBySystemTime(CompareBySystemTime comparator, DateHolder dateHolder, Blackhole blackhole) {
		for ( Date date : dateHolder.dates ) {
			blackhole.consume( comparator.isBefore( date ) );
		}
	}

	@Benchmark
	@Fork(1)
	@OperationsPerInvocation(201)
	public void compareByCalendar(CompareByCalendar comparator, DateHolder dateHolder, Blackhole blackhole) {
		for ( Date date : dateHolder.dates ) {
			blackhole.consume( comparator.isBefore( date ) );
		}
	}

	public static void main(String[] args) throws Exception {
		Options opt = new OptionsBuilder()
				.include( ".*" + DateConstraintBenchmark.class.getSimpleName() + ".*" )
				.build();
		new Runner( opt ).run();
	}
}
