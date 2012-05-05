package net.fwbrasil.activate.entity;

import java.io.IOException;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

class Dummy {
	String length = "test";

	void print() {
		System.out.println(length);
	}

}

public class JavassistLengthBug {

	public static void main(String[] args) throws NotFoundException,
			CannotCompileException, IOException {
		ClassPool classPool = ClassPool.getDefault();
		classPool.appendClassPath(new ClassClassPath(JavassistLengthBug.class));
		CtClass dummyClass = classPool
				.get("net.fwbrasil.activate.entity.Dummy");
		dummyClass.instrument(new ExprEditor() {
			public void edit(FieldAccess fa) {
				if (fa.isReader() && fa.getFieldName().equals("length"))
					try {
						fa.replace("$_ = ($r) this.length.substring(1);");
					} catch (CannotCompileException e) {
						e.printStackTrace();
					}
			}
		});
		dummyClass.toClass();
		new Dummy().print();
	}
}
