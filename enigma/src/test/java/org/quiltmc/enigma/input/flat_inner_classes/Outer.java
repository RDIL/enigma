package org.quiltmc.enigma.input.flat_inner_classes;

public class Outer {
	private final Inner inner = new Inner();

	public Inner getInner() {
		return this.inner;
	}

	public static class Inner {
		public void run() {
		}
	}
}
