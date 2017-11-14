package io.vrap.rmf.raml.persistence.constructor;

import io.vrap.rmf.raml.model.types.*;
import io.vrap.rmf.raml.persistence.antlr.ParserErrorCollector;
import io.vrap.rmf.raml.persistence.antlr.TypeExpressionBaseVisitor;
import io.vrap.rmf.raml.persistence.antlr.TypeExpressionLexer;
import io.vrap.rmf.raml.persistence.antlr.TypeExpressionParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.util.stream.Collectors;

import static io.vrap.rmf.raml.model.modules.ModulesPackage.Literals.TYPE_CONTAINER__ANNOTATION_TYPES;
import static io.vrap.rmf.raml.model.modules.ModulesPackage.Literals.TYPE_CONTAINER__TYPES;
import static io.vrap.rmf.raml.model.types.TypesPackage.Literals.*;

/**
 * This class parses a type expression and resolves it to an {@link AnyType}.
 *
 * This visitor works returns an {@link EObject} because it can resolve types and
 * annotation types.
 */
public class TypeExpressionResolver {

    public EObject resolve(final String typeExpression, final Scope scope) {
        final CharStream charStream = CharStreams.fromString(typeExpression);
        final TypeExpressionLexer lexer = new TypeExpressionLexer(charStream);
        final TokenStream tokenStream = new CommonTokenStream(lexer);
        final TypeExpressionParser typeExpressionParser = new TypeExpressionParser(tokenStream);

        lexer.removeErrorListeners();
        typeExpressionParser.removeErrorListeners();

        final ParserErrorCollector errorCollector = new ParserErrorCollector();
        lexer.addErrorListener(errorCollector);
        typeExpressionParser.addErrorListener(errorCollector);

        final TypeExpressionParser.Type_exprContext typeExpr = typeExpressionParser.type_expr();

        final EObject resolvedElement;
        final EStructuralFeature feature = scope.eFeature();
        if (feature == TYPED_ELEMENT__TYPE || feature == ARRAY_TYPE_FACET__ITEMS) {
            resolvedElement = new TypedElementTypeResolver(scope).visit(typeExpr);
        } else if (feature == TYPE_CONTAINER__TYPES) {
            resolvedElement = new TypeResolver(scope).visit(typeExpr);
        } else if (feature == TYPE_CONTAINER__ANNOTATION_TYPES) {
            resolvedElement = new AnnotationTypeResolver(scope).visit(typeExpr);
        } else if (feature == ANY_TYPE__TYPE) {
            resolvedElement = new AnyTypeTypeResolver(scope).visit(typeExpr);
        } else {
            resolvedElement = null; // TODO report error/throw exception
        }
        scope.getResource().getErrors().addAll(errorCollector.getErrors());

        return resolvedElement;
    }

    private final static class TypedElementTypeResolver extends TypeExpressionBaseVisitor<EObject> {
        private final Scope scope;

        public TypedElementTypeResolver(final Scope scope) {
            this.scope = scope;
        }

        @Override
        public EObject visitParens(final TypeExpressionParser.ParensContext ctx) {
            return super.visit(ctx.type_expr());
        }

        @Override
        public EObject visitArrayType(final TypeExpressionParser.ArrayTypeContext ctx) {
            final EObject arrayType = EcoreUtil.create(ARRAY_TYPE);
            scope.addValue(INLINE_TYPE_CONTAINER__INLINE_TYPES, arrayType);
            final EObject itemsType = visit(ctx.primary_type_expr());
            arrayType.eSet(ARRAY_TYPE_FACET__ITEMS, itemsType);

            return arrayType;
        }

        @Override
        public EObject visitUnionType(final TypeExpressionParser.UnionTypeContext ctx) {
            final UnionType unionType = (UnionType) EcoreUtil.create(UNION_TYPE);
            scope.addValue(INLINE_TYPE_CONTAINER__INLINE_TYPES, unionType);
            final EList<AnyType> oneOfType = ECollections.asEList(ctx.primary_type_expr().stream()
                    .map(this::visit)
                    .filter(AnyType.class::isInstance) // TODO report errors
                    .map(AnyType.class::cast)
                    .collect(Collectors.toList()));
            unionType.getOneOf().addAll(oneOfType);

            return unionType;
        }

        @Override
        public EObject visitTypeTemplate(TypeExpressionParser.TypeTemplateContext ctx) {
            final TypeTemplate typeTemplate = TypesFactory.eINSTANCE.createTypeTemplate();
            scope.addValue(INLINE_TYPE_CONTAINER__INLINE_TYPES, typeTemplate);
            typeTemplate.setName(ctx.getText());
            return typeTemplate;
        }

        @Override
        public EObject visitTypeReference(final TypeExpressionParser.TypeReferenceContext ctx) {
            final String typeName = ctx.getText();
            final EObject anyType = scope.getEObjectByName(typeName);

            return anyType;
        }
    }

    private final static class TypeResolver extends TypeExpressionBaseVisitor<EObject> {
        private final Scope scope;
        private boolean nestedTypes;

        public TypeResolver(final Scope scope) {
            this.scope = scope;
            this.nestedTypes = false;
        }

        @Override
        public EObject visitParens(TypeExpressionParser.ParensContext ctx) {
            return super.visit(ctx.type_expr());
        }

        @Override
        public EObject visitArrayType(final TypeExpressionParser.ArrayTypeContext ctx) {
            final EObject arrayType = EcoreUtil.create(ARRAY_TYPE);
            this.nestedTypes = true;
            final EObject itemsType = visit(ctx.primary_type_expr());
            arrayType.eSet(ARRAY_TYPE_FACET__ITEMS, itemsType);

            return arrayType;
        }

        @Override
        public EObject visitUnionType(final TypeExpressionParser.UnionTypeContext ctx) {
            final EObject unionType = EcoreUtil.create(UNION_TYPE);
            this.nestedTypes = true;
            final EList<AnyType> oneOfType = ECollections.asEList(ctx.primary_type_expr().stream()
                    .map(this::visit)
                    .filter(AnyType.class::isInstance) // TODO report errors
                    .map(AnyType.class::cast)
                    .collect(Collectors.toList()));
            unionType.eSet(ONE_OF_FACET__ONE_OF, oneOfType);

            return unionType;
        }

        @Override
        public EObject visitTypeTemplate(final TypeExpressionParser.TypeTemplateContext ctx) {
            scope.addError("Type template not allowed here.");
            return null;
        }

        @Override
        public EObject visitTypeReference(final TypeExpressionParser.TypeReferenceContext ctx) {
            final String typeName = ctx.getText();
            final BuiltinType builtinType = BuiltinType.of(typeName).orElse(null);
            final EObject resolved;
            if (nestedTypes) {
                resolved = scope.getEObjectByName(typeName);
            } else if (builtinType == null) {
                final EObject eObject = scope.getEObjectByName(typeName);
                resolved = eObject.eIsProxy() ? eObject : EcoreUtil.create(eObject.eClass());
            } else {
                resolved = EcoreUtil.create(builtinType.getTypeDeclarationType());
            }
            return resolved;
        }
    }


    private final static class AnyTypeTypeResolver extends TypeExpressionBaseVisitor<EObject> {
        private final Scope scope;

        public AnyTypeTypeResolver(final Scope scope) {
            this.scope = scope;
        }

        @Override
        public EObject visitParens(TypeExpressionParser.ParensContext ctx) {
            return super.visit(ctx.type_expr());
        }

        @Override
        public EObject visitTypeReference(final TypeExpressionParser.TypeReferenceContext ctx) {
            final String typeName = ctx.getText();

            return BuiltinType.of(typeName).isPresent() ?
                    null :
                    scope.getEObjectByName(typeName);
        }
    }

    private final static class AnnotationTypeResolver extends TypeExpressionBaseVisitor<EObject> {
        private final Scope scope;
        private boolean nestedTypes;

        public AnnotationTypeResolver(final Scope scope) {
            this.scope = scope;
            this.nestedTypes = false;
        }

        @Override
        public EObject visitParens(TypeExpressionParser.ParensContext ctx) {
            return super.visit(ctx.type_expr());
        }

        @Override
        public EObject visitArrayType(final TypeExpressionParser.ArrayTypeContext ctx) {
            final EObject arrayType = EcoreUtil.create(ARRAY_ANNOTATION_TYPE);
            this.nestedTypes = true;
            final EObject itemsType = visit(ctx.primary_type_expr());
            arrayType.eSet(ARRAY_TYPE_FACET__ITEMS, itemsType);

            return arrayType;
        }

        @Override
        public EObject visitUnionType(final TypeExpressionParser.UnionTypeContext ctx) {
            final EObject unionType = EcoreUtil.create(UNION_ANNOTATION_TYPE);
            this.nestedTypes = true;
            final EList<AnyType> oneOfType = ECollections.asEList(ctx.primary_type_expr().stream()
                    .map(this::visit)
                    .filter(AnyType.class::isInstance) // TODO report errors
                    .map(AnyType.class::cast)
                    .collect(Collectors.toList()));
            unionType.eSet(ONE_OF_FACET__ONE_OF, oneOfType);

            return unionType;
        }

        @Override
        public EObject visitTypeTemplate(final TypeExpressionParser.TypeTemplateContext ctx) {
            scope.addError("Type template not allowed here.");
            return null;
        }

        @Override
        public EObject visitTypeReference(final TypeExpressionParser.TypeReferenceContext ctx) {
            final String typeName = ctx.getText();
            final BuiltinType builtinType = BuiltinType.of(typeName).orElse(null);
            final EObject annotationType;
            if (builtinType == null) {
                scope.addError("Type {0} can't be used as annotation type", typeName);
                annotationType = null;
            } else {
                annotationType = nestedTypes ?
                        builtinType.getEObject(scope.getResource().getResourceSet()) :
                        EcoreUtil.create(builtinType.getAnnotationTypeDeclarationType());
            }
            return annotationType;
        }
    }
}
