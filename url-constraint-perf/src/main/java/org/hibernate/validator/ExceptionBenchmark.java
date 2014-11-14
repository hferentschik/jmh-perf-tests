package org.hibernate.validator;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(5)
public class ExceptionBenchmark {

	LilException staticException;

	int source = 42;

	@Setup(Level.Iteration)
	public void setup() {
		staticException = new LilException( source );
	}

	@CompilerControl(CompilerControl.Mode.INLINE)
	public int doSomething() {
		return source;
	}

	@CompilerControl(CompilerControl.Mode.INLINE)
	public int doSomething_Exception() throws LilException {
		throw new LilException( source );
	}

	@CompilerControl(CompilerControl.Mode.INLINE)
	public int doSomething_Exception_Static() throws LilException {
		throw staticException;
	}

	@Benchmark
	public int plain() {
		return doSomething();
	}

	@Benchmark
	public int dynamicException() {
		try {
			return doSomething_Exception();
		}
		catch ( LilException e ) {
			return source;
		}
	}

	@Benchmark
	public int dynamicException_UsedData() {
		try {
			return doSomething_Exception();
		}
		catch ( LilException e ) {
			return e.getMetadata();
		}
	}


	@Benchmark
	public int dynamicException_UsedStack() {
		try {
			return doSomething_Exception();
		}
		catch ( LilException e ) {
			return e.getStackTrace().length;
		}
	}

	@Benchmark
	@Fork(jvmArgs = "-XX:-StackTraceInThrowable")
	public int dynamicException_NoStack() {
		try {
			return doSomething_Exception();
		}
		catch ( LilException e ) {
			return source;
		}
	}

	@Benchmark
	@Fork(jvmArgs = "-XX:-StackTraceInThrowable")
	public int dynamicException_NoStack_UsedData() {
		try {
			return doSomething_Exception();
		}
		catch ( LilException e ) {
			return e.getMetadata();
		}
	}

	@Benchmark
	@Fork(jvmArgs = "-XX:-StackTraceInThrowable")
	public int dynamicException_NoStack_UsedStack() {
		try {
			return doSomething_Exception();
		}
		catch ( LilException e ) {
			return e.getStackTrace().length;
		}
	}

	@Benchmark
	public int staticException() {
		try {
			return doSomething_Exception_Static();
		}
		catch ( LilException e ) {
			return source;
		}
	}

	@Benchmark
	public int staticException_UsedData() {
		try {
			return doSomething_Exception_Static();
		}
		catch ( LilException e ) {
			return e.getMetadata();
		}
	}

	@Benchmark
	public int staticException_UsedStack() {
		try {
			return doSomething_Exception_Static();
		}
		catch ( LilException e ) {
			return e.getStackTrace().length;
		}
	}

	@Benchmark
	@Fork(jvmArgs = "-XX:-StackTraceInThrowable")
	public int staticException_NoStack() {
		try {
			return doSomething_Exception_Static();
		}
		catch ( LilException e ) {
			return source;
		}
	}

	@Benchmark
	@Fork(jvmArgs = "-XX:-StackTraceInThrowable")
	public int staticException_NoStack_UsedData() {
		try {
			return doSomething_Exception_Static();
		}
		catch ( LilException e ) {
			return e.getMetadata();
		}
	}

	@Benchmark
	@Fork(jvmArgs = "-XX:-StackTraceInThrowable")
	public int staticException_NoStack_UsedStack() {
		try {
			return doSomething_Exception_Static();
		}
		catch ( LilException e ) {
			return e.getStackTrace().length;
		}
	}

	public static void main(String[] args) throws Exception {
		Options opt = new OptionsBuilder()
				.include( ".*" + ExceptionBenchmark.class.getSimpleName() + ".*" )
				.mode( Mode.AverageTime )
				.forks( 5 )
				.warmupIterations( 1 )
				.warmupTime( TimeValue.milliseconds( 10 ) )
				.measurementIterations( 3 )
				.measurementTime( TimeValue.seconds( 1 ) )
				.timeUnit( TimeUnit.NANOSECONDS )
				.build();
		new Runner( opt ).run();
	}

}



