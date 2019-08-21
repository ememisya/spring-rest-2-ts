package com.blueveery.springrest2ts.examples.test;


import com.blueveery.springrest2ts.GenerationContext;
import com.blueveery.springrest2ts.SpringREST2tsGenerator;
import com.blueveery.springrest2ts.SubstringClassNameMapper;
import com.blueveery.springrest2ts.converters.ModulePerJavaPackageConverter;
import com.blueveery.springrest2ts.converters.TypeMapper;
import com.blueveery.springrest2ts.examples.ctrls.OrderCtrl;
import com.blueveery.springrest2ts.examples.ctrls.core.BaseCtrl;
import com.blueveery.springrest2ts.examples.model.CategoryDTO;
import com.blueveery.springrest2ts.examples.model.OrderDTO;
import com.blueveery.springrest2ts.examples.model.core.BaseDTO;
import com.blueveery.springrest2ts.examples.model.enums.OrderPaymentStatus;
import com.blueveery.springrest2ts.filters.*;
import com.blueveery.springrest2ts.implgens.Angular4ImplementationGenerator;
import com.blueveery.springrest2ts.tsmodel.TSModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

public class OrderSetupTest {

    private static final Path OUTPUT_DIR_PATH = Paths.get("../../../test-webapp/src/");

    private static SpringREST2tsGenerator tsGenerator;
    private static ModulePerJavaPackageConverter moduleConverter;

    @Before
    public void setUp() throws IOException {
        FileSystemUtils.deleteRecursively(OUTPUT_DIR_PATH.resolve("app/sdk").toFile());

        tsGenerator = new SpringREST2tsGenerator();
        tsGenerator.setModelClassesCondition(new ExtendsJavaTypeFilter(BaseDTO.class));
        tsGenerator.setRestClassesCondition(new ExtendsJavaTypeFilter(BaseCtrl.class));
        tsGenerator.setGenerationContext(new GenerationContext(new Angular4ImplementationGenerator()));

        HashMap<String, TSModule> packagesMap = new HashMap<>();
        packagesMap.put(BaseDTO.class.getPackage().getName(), new TSModule("core", Paths.get("app/sdk/model"), false));
        packagesMap.put(OrderDTO.class.getPackage().getName(), new TSModule("model", Paths.get("app/sdk/model"), false));
        packagesMap.put(OrderPaymentStatus.class.getPackage().getName(), new TSModule("model-enums", Paths.get("app/sdk/enums"), false));
        packagesMap.put(OrderCtrl.class.getPackage().getName(), new TSModule("services", Paths.get("app/sdk/services"), false));

        tsGenerator.getPackagesNames().addAll(packagesMap.keySet());
        moduleConverter = new ModulePerJavaPackageConverter(packagesMap);
    }

    @Test
    public void defaultSetup() throws IOException {
        tsGenerator.generate(moduleConverter, OUTPUT_DIR_PATH);
    }

    @Test
    public void classNameMappingTest() throws IOException {
        tsGenerator.setModelClassesNameMapper(new SubstringClassNameMapper("DTO", ""));
        tsGenerator.setRestClassesNameMapper(new SubstringClassNameMapper("Ctrl", "Service"));
        tsGenerator.generate(moduleConverter, OUTPUT_DIR_PATH);
    }

    @Test
    public void containsSubStringClassCondition() throws IOException {
        tsGenerator.setModelClassesCondition(new ContainsSubStringJavaTypeFilter("DTO"));
        tsGenerator.setRestClassesCondition(new ContainsSubStringJavaTypeFilter("Ctrl"));
        tsGenerator.generate(moduleConverter, OUTPUT_DIR_PATH);
    }

    @Test
    public void regexClassCondition() throws IOException {
        tsGenerator.setModelClassesCondition(new RegexpJavaTypeFilter("\\w*DTO\\b"));
        tsGenerator.setRestClassesCondition(new RegexpJavaTypeFilter("\\w*Ctrl\\b"));
        tsGenerator.generate(moduleConverter, OUTPUT_DIR_PATH);
    }


    @Test
    public void complexRestClassCondition() throws IOException {
        OrFilterOperator annotationConditions = new OrFilterOperator(Arrays.asList(new HasAnnotationJavaTypeFilter(Controller.class), new HasAnnotationJavaTypeFilter(RestController.class)));
        tsGenerator.setRestClassesCondition(new AndFilterOperator(Arrays.asList(new ExtendsJavaTypeFilter(BaseCtrl.class), annotationConditions)));
        tsGenerator.generate(moduleConverter, OUTPUT_DIR_PATH);
    }

    @Test
    public void customTypeMapping() throws IOException {
        tsGenerator.getCustomTypeMapping().put(UUID.class, TypeMapper.tsString);
        tsGenerator.getCustomTypeMapping().put(BigInteger.class, TypeMapper.tsNumber);
        tsGenerator.getCustomTypeMapping().put(LocalDateTime.class, TypeMapper.tsDate);
        tsGenerator.getCustomTypeMapping().put(LocalDate.class, TypeMapper.tsDate);
        tsGenerator.generate(moduleConverter, OUTPUT_DIR_PATH);
    }

    @Test
    public void jacksonSetterGetters() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        CategoryDTO category = new CategoryDTO("Phones");
        System.out.println(objectMapper.writeValueAsString(category));
    }

}
