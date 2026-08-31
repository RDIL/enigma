package org.quiltmc.enigma;

import org.junit.jupiter.api.Test;
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
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class TestFlatInnerClasses {
	private static final Path NESTED_JAR = TestUtil.obfJar("inner_classes");
	private static final Path FLAT_JAR = TestUtil.obfJar("flat_inner_classes");

	private static final ClassEntry NESTED_OUTER = TestEntryFactory.newClass("d");
	private static final ClassEntry NESTED_INNER = TestEntryFactory.newClass("d$a");

	private static final ClassEntry FLAT_OUTER = TestEntryFactory.newClass("org/quiltmc/enigma/input/flat_inner_classes/Outer");
	private static final ClassEntry FLAT_INNER = TestEntryFactory.newClass("org/quiltmc/enigma/input/flat_inner_classes/Outer$Inner");

	@Test
	public void nestedInSourceWhenDeclared() throws IOException {
		InnerClassIndex innerClasses = index(NESTED_JAR).getIndex(InnerClassIndex.class);

		assertThat(innerClasses.isNestedInSource(NESTED_INNER), is(true));
		assertThat(innerClasses.getSourceRoot(NESTED_INNER), is(NESTED_OUTER));
		assertThat(innerClasses.getSourceRoot(NESTED_OUTER), is(NESTED_OUTER));
	}

	@Test
	public void notNestedInSourceWithoutTheAttribute() throws IOException {
		InnerClassIndex innerClasses = index(FLAT_JAR).getIndex(InnerClassIndex.class);

		// the name still reads as nested, and the mappings still treat it as an inner class
		assertThat(FLAT_INNER.isInnerClass(), is(true));

		assertThat(innerClasses.isNestedInSource(FLAT_INNER), is(false));
		assertThat(innerClasses.getSourceRoot(FLAT_INNER), is(FLAT_INNER));
	}

	@Test
	public void declaredInnerClassIsDecompiledIntoItsOuterClass() throws IOException {
		SourceIndex source = decompile(NESTED_JAR, NESTED_OUTER);

		assertThat(source.getDeclarationToken(NESTED_OUTER), is(notNullValue()));
		assertThat(source.getDeclarationToken(NESTED_INNER), is(notNullValue()));
	}

	@Test
	public void flatInnerClassIsDecompiledOnItsOwn() throws IOException {
		SourceIndex innerSource = decompile(FLAT_JAR, FLAT_INNER);
		assertThat(innerSource.getDeclarationToken(FLAT_INNER), is(notNullValue()));

		// its tokens can't leak into the class it is named after, whose file does not contain it
		SourceIndex outerSource = decompile(FLAT_JAR, FLAT_OUTER);
		assertThat(outerSource.getDeclarationToken(FLAT_OUTER), is(notNullValue()));
		assertThat(outerSource.getDeclarationToken(FLAT_INNER), is(nullValue()));
	}

	private static SourceIndex decompile(Path jar, ClassEntry entry) throws IOException {
		ClassProvider classProvider = new CachingClassProvider(new JarClassProvider(jar));
		return Decompilers.VINEFLOWER.create(classProvider, new SourceSettings(false, false))
				.getUndocumentedSource(entry.getFullName())
				.index();
	}

	private static JarIndex index(Path jar) throws IOException {
		JarIndex index = MainJarIndex.empty();
		ClassProvider classProvider = new CachingClassProvider(new JarClassProvider(jar));
		index.indexJar(new ProjectClassProvider(classProvider, null), ProgressListener.createEmpty());
		return index;
	}
}
