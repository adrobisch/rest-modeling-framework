package io.vrap.rmf.raml.model.util

import io.vrap.rmf.raml.model.modules.Library
import io.vrap.rmf.raml.model.modules.ModulesFactory
import io.vrap.rmf.raml.model.types.*
import io.vrap.rmf.raml.persistence.RamlResourceSet
import org.eclipse.emf.common.util.EList
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import spock.lang.Shared
import spock.lang.Specification
import org.eclipse.emf.common.util.URI

/**
 * Unit tests for {@link ModelHelper}.
 */
class ModelHelperTest extends Specification {
    @Shared
    ResourceSet resourceSet = new RamlResourceSet()

    def "getAllProperties returns most specific property"() {
        when:
        ObjectType parent = objectTypeWithProperty("age", BuiltinType.NUMBER)
        ObjectType child = objectTypeWithProperty("age", BuiltinType.INTEGER)
        child.setType(parent)

        then:
        EList<Property> allProperties = ModelHelper.getAllProperties(child);
        allProperties.size() == 1
        allProperties[0].name == 'age'
        allProperties[0].type instanceof IntegerType
    }

    def "getSubTypes returns all subtypes"() {
        when:
        ResourceSet resourceSet = new RamlResourceSet()
        Resource resource = resourceSet.createResource(URI.createFileURI("test.raml"))

        Library library = ModulesFactory.eINSTANCE.createLibrary()
        resource.contents.add(library)

        ObjectType superType = TypesFactory.eINSTANCE.createObjectType()
        library.types.add(superType)

        ObjectType subType = TypesFactory.eINSTANCE.createObjectType()
        library.types.add(subType)
        subType.setType(superType)

        then:
        superType.getSubTypes().size() == 1
    }

    ObjectType objectTypeWithProperty(String propertyName, BuiltinType propertyType) {
        Property property = TypesFactory.eINSTANCE.createProperty()
        property.name = propertyName
        property.type = propertyType.getType(resourceSet)

        ObjectType objectType = TypesFactory.eINSTANCE.createObjectType()
        objectType.properties.add(property)

        return objectType;
    }
}
