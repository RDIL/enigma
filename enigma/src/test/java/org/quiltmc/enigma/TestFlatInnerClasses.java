package org.quiltmc.enigma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.quiltmc.enigma.api.ProgressListener;
import org.quiltmc.enigma.api.analysis.index.jar.InnerClassIndex;
import org.quiltmc.enigma.api.analysis.index.jar.JarIndex;
import org.quiltmc.enigma.api.analysis.index.jar.MainJarIndex;
import org.quiltmc.enigma.api.class_provider.CachingClassProvider;
import org.quiltmc.enigma.api.class_provider.ClassProvider;
import org.quiltmc.enigma.api.class_provider.JarClassProvider;
import org.quiltmc.enigma.api.class_provider.ProjectClassProvider;
import org.quiltmc.enigma.api.source.Decompilers;
import org.quiltmc.enigma.api.source.SourceIndex;
import org.quiltmc.enigma.api.source.SourceSettings;
import org.quiltmc.enigma.api.translation.representation.entry.ClassEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class TestFlatInnerClasses {
	private static final ClassEntry OUTER = new ClassEntry("a/Outer");
	private static final ClassEntry INNER = new ClassEntry("a/Outer$Inner");

	@TempDir
	private Path tempDir;

	@Test
	public void nestedInSourceWhenDeclared() throws IOException {
		JarIndex index = this.index(this.writeJar("declared.jar", true));
		InnerClassIndex innerClasses = index.getIndex(InnerClassIndex.class);

		assertThat(innerClasses.isNestedInSource(INNER), is(true));
		assertThat(innerClasses.getSourceRoot(INNER), is(OUTER));
		assertThat(innerClasses.getSourceRoot(OUTER), is(OUTER));
	}

	@Test
	public void notNestedInSourceWithoutTheAttribute() throws IOException {
		JarIndex index = this.index(this.writeJar("stripped.jar", false));
		InnerClassIndex innerClasses = index.getIndex(InnerClassIndex.class);

		// the name still reads as nested, and the mappings still treat it as an inner class
		assertThat(INNER.isInnerClass(), is(true));

		assertThat(innerClasses.isNestedInSource(INNER), is(false));
		assertThat(innerClasses.getSourceRoot(INNER), is(INNER));
	}

	@Test
	public void declaredInnerClassIsDecompiledIntoItsOuterClass() throws IOException {
		SourceIndex source = this.decompile(this.writeJar("declared.jar", true), OUTER);

		assertThat(source.getDeclarationToken(OUTER), is(notNullValue()));
		assertThat(source.getDeclarationToken(INNER), is(notNullValue()));
	}

	@Test
	public void flatInnerClassIsDecompiledOnItsOwn() throws IOException {
		Path jar = this.writeJar("stripped.jar", false);

		SourceIndex innerSource = this.decompile(jar, INNER);
		assertThat(innerSource.getDeclarationToken(INNER), is(notNullValue()));

		// its tokens can't leak into the class it is named after, whose file does not contain it
		SourceIndex outerSource = this.decompile(jar, OUTER);
		assertThat(outerSource.getDeclarationToken(OUTER), is(notNullValue()));
		assertThat(outerSource.getDeclarationToken(INNER), is(nullValue()));
	}

	private SourceIndex decompile(Path jar, ClassEntry entry) throws IOException {
		ClassProvider classProvider = new CachingClassProvider(new JarClassProvider(jar));
		return Decompilers.VINEFLOWER.create(classProvider, new SourceSettings(false, false))
				.getUndocumentedSource(entry.getFullName())
				.index();
	}

	private JarIndex index(Path jar) throws IOException {
		JarIndex index = MainJarIndex.empty();
		ClassProvider classProvider = new CachingClassProvider(new JarClassProvider(jar));
		index.indexJar(new ProjectClassProvider(classProvider, null), ProgressListener.createEmpty());
		return index;
	}

	private Path writeJar(String name, boolean declareNesting) throws IOException {
		Path jar = this.tempDir.resolve(name);
		if (Files.exists(jar)) {
			return jar;
		}

		try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
			write(out, OUTER, declareNesting);
			write(out, INNER, declareNesting);
		}

		return jar;
	}

	private static void write(JarOutputStream out, ClassEntry entry, boolean declareNesting) throws IOException {
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, entry.getFullName(), null, "java/lang/Object", null);

		if (declareNesting) {
			writer.visitInnerClass(INNER.getFullName(), OUTER.getFullName(), "Inner", Opcodes.ACC_STATIC);
		}

		MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(1, 1);
		init.visitEnd();

		MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
		method.visitCode();
		method.visitInsn(Opcodes.RETURN);
		method.visitMaxs(0, 1);
		method.visitEnd();

		writer.visitEnd();

		out.putNextEntry(new JarEntry(entry.getFullName() + ".class"));
		out.write(writer.toByteArray());
		out.closeEntry();
	}
}
