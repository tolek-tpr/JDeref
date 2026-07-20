package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.ArrayList;

public class JDerefTranslator extends TreeTranslator {

    private final TreeMaker make;
    private final Names names;
    private final Types types;
    private final Symtab syms;

    public JDerefTranslator(TreeMaker make, Names names, Types types, Symtab syms) {
        this.make = make;
        this.names = names;
        this.types = types;
        this.syms = syms;
    }

    private JCTree.JCTypeApply isDeref(JCTree.JCClassDecl tree) {
        for (JCTree.JCExpression expr : tree.implementing) {
            if (!(expr instanceof JCTree.JCTypeApply apply))
                continue;

            if (apply.clazz instanceof JCTree.JCIdent ident &&
                    ident.name.contentEquals("Deref")) {
                return apply;
            }
        }

        return null;
    }

    private boolean sameSignature(Symbol.MethodSymbol a, Symbol.MethodSymbol b) {
        if (!a.name.equals(b.name))
            return false;

        if (!types.isSameType(a.getReturnType(), b.getReturnType()))
            return false;

        List<Type> pa = a.type.getParameterTypes();
        List<Type> pb = b.type.getParameterTypes();

        if (pa.size() != pb.size())
            return false;

        while (!pa.isEmpty()) {
            if (!types.isSameType(pa.head, pb.head))
                return false;

            pa = pa.tail;
            pb = pb.tail;
        }

        return true;
    }

    private Symbol.ClassSymbol getWrappedClass(JCTree.JCExpression expr) {
        Type type = expr.type;
        if (type == null)
            return null;

        return (Symbol.ClassSymbol) type.tsym;
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        JCTree.JCTypeApply deref = isDeref(tree);
        if (deref == null) {
            super.visitClassDef(tree);
            return;
        }

        Symbol.ClassSymbol wrapper = tree.sym;
        Symbol.ClassSymbol wrapped = getWrappedClass(deref.arguments.head);

        if (wrapped == null) {
            super.visitClassDef(tree);
            return;
        }

        java.util.List<Symbol.MethodSymbol> methodsToDuplicate = new java.util.ArrayList<>();

        for (Symbol s1 : wrapper.members().getSymbols()) {
            if (s1 instanceof Symbol.MethodSymbol m1 && !m1.isConstructor() && !m1.isStatic()) {
                for (Symbol s2 : wrapped.members().getSymbolsByName(m1.name)) {
                    if (s2 instanceof Symbol.MethodSymbol m2 && sameSignature(m1, m2)) {
                        methodsToDuplicate.add(m1);
                        break;
                    }
                }
            }
        }

        ListBuffer<JCTree> newDefs = new ListBuffer<>();
        Scope.WriteableScope writeableScope = wrapper.members();

        for (Symbol.MethodSymbol origSym : methodsToDuplicate) {
            Name newName = names.fromString(origSym.name.toString() + "$Static");

            make.at(tree.pos);

            ListBuffer<Type> argTypes = new ListBuffer<>();
            argTypes.append(wrapper.type);
            for (Type t : origSym.type.getParameterTypes()) {
                argTypes.append(t);
            }

            Type.MethodType newMethodType = new Type.MethodType(
                    argTypes.toList(),
                    origSym.getReturnType(),
                    origSym.getThrownTypes(),
                    syms.methodClass
            );

            Symbol.MethodSymbol newSym = new Symbol.MethodSymbol(
                    Flags.PUBLIC | Flags.STATIC,
                    newName,
                    newMethodType,
                    wrapper
            );

            ListBuffer<JCTree.JCVariableDecl> params = new ListBuffer<>();
            ListBuffer<Symbol.VarSymbol> paramSymbols = new ListBuffer<>();
            ListBuffer<JCTree.JCExpression> callArgs = new ListBuffer<>();

            Symbol.VarSymbol instanceSym = new Symbol.VarSymbol(
                    Flags.PARAMETER, names.fromString("instance"), wrapper.type, newSym);

            instanceSym.pos = tree.pos;

            params.append(make.VarDef(instanceSym, null));
            paramSymbols.append(instanceSym);

            for (Symbol.VarSymbol origParam : origSym.params()) {
                Symbol.VarSymbol newParam = new Symbol.VarSymbol(
                        Flags.PARAMETER, origParam.name, origParam.type, newSym);

                newParam.pos = tree.pos;

                params.append(make.VarDef(newParam, null));
                paramSymbols.append(newParam);

                callArgs.append(make.Ident(newParam.name));
            }

            newSym.params = paramSymbols.toList();

            JCTree.JCExpression receiver = make.Ident(names.fromString("instance"));
            JCTree.JCExpression select = make.Select(receiver, origSym.name);
            JCTree.JCExpression call = make.Apply(List.nil(), select, callArgs.toList());

            JCTree.JCStatement statement = origSym.getReturnType().hasTag(TypeTag.VOID)
                    ? make.Exec(call)
                    : make.Return(call);

            JCTree.JCBlock body = make.Block(0, List.of(statement));

            JCTree.JCMethodDecl newMethodDecl = make.MethodDef(
                    make.Modifiers(Flags.PUBLIC | Flags.STATIC),
                    newName,
                    make.Type(origSym.getReturnType()),
                    List.nil(),
                    params.toList(),
                    make.Types(origSym.getThrownTypes()),
                    body,
                    null
            );
            newMethodDecl.sym = newSym;

            writeableScope.enter(newSym);
            newDefs.append(newMethodDecl);
        }

        if (newDefs.nonEmpty()) {
            tree.defs = tree.defs.appendList(newDefs.toList());
        }
        super.visitClassDef(tree);
    }

}
