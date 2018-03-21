package io.vrap.rmf.raml.generic.generator.php;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import io.vrap.rmf.raml.generic.generator.AbstractTemplateGenerator;
import io.vrap.rmf.raml.generic.generator.CollectionGenModel;
import io.vrap.rmf.raml.generic.generator.TypeGenModel;
import io.vrap.rmf.raml.generic.generator.postman.ProjectGenModel;
import io.vrap.rmf.raml.generic.generator.postman.ResourceGenModel;
import io.vrap.rmf.raml.model.modules.Api;
import io.vrap.rmf.raml.model.resources.HttpMethod;
import io.vrap.rmf.raml.model.types.*;
import io.vrap.rmf.raml.model.types.impl.TypesFactoryImpl;
import io.vrap.rmf.raml.model.types.util.TypesSwitch;
import org.eclipse.emf.ecore.EObject;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroupFile;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BuilderGenerator extends AbstractTemplateGenerator {
    public static String BUILDER = "Builder";
    private static final String resourcesPath = "./templates/php/";
    static final String TYPE_BUILDER = "builder";
    private final String vendorName;

    BuilderGenerator(final String vendorName)
    {
        this.vendorName = vendorName;
    }

    public List<File> generate(Api api, final File outputPath) throws IOException {

        final List<File> f = Lists.newArrayList();

        final List<BuilderGenModel> builders = Lists.newArrayList();

        builders.addAll(
                api.getTypes().stream()
                        .filter(type -> type.getAnnotation("updateType") != null)
                        .map(BuilderGenModel::new)
                .collect(Collectors.toList()));

        f.addAll(generateBuilders(outputPath, builders));

        return f;
    }


    private List<File> generateBuilders(final File outputPath, List<BuilderGenModel> builders) throws IOException {
        final List<File> f = Lists.newArrayList();
        for (final BuilderGenModel builder : builders) {

            final File builderFile = new File(outputPath, builder.getUpdateType().getName().concat("Builder.php"));

            f.add(generateFile(generateBuilder(builder), builderFile));
//            for(final TypeGenModel updateAction : builder.getUpdates()) {
//                final File actionBuilderFile = new File(outputPath, updateAction.getName().concat("Builder.php"));
//                f.add(generateFile(generateActionBuilder(updateAction), actionBuilderFile));
//            }
        }
        return f;
    }

    @VisibleForTesting
    String generateBuilder(BuilderGenModel builder) {
        final STGroupFile stGroup = createSTGroup(Resources.getResource(resourcesPath + TYPE_BUILDER + ".stg"));
        final ST st = stGroup.getInstanceOf("updateBuilder");
        st.add("vendorName", vendorName);
        st.add("builder", builder);
        return st.render();
    }

//    @VisibleForTesting
//    String generateActionBuilder(TypeGenModel updateAction) {
//        final STGroupFile stGroup = createSTGroup(Resources.getResource(resourcesPath + TYPE_BUILDER + ".stg"));
//        final ST st = stGroup.getInstanceOf("actionBuilder");
//        st.add("vendorName", vendorName);
//        st.add("type", updateAction);
//        return st.render();
//    }
}
