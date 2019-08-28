package com.blueveery.springrest2ts.implgens;

import com.blueveery.springrest2ts.converters.ModuleConverter;
import com.blueveery.springrest2ts.converters.TypeMapper;
import com.blueveery.springrest2ts.tsmodel.*;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class Angular4ImplementationGenerator extends SpringMvcImplementationGenerator implements ImplementationGenerator {
    private static final String FIELD_NAME_HTTP_SERVICE = "httpService";
    private static final String FIELD_NAME_URL_SERVICE = "urlService";
    private static final String FIELD_NAME_SUBJECT = "subject";

    private TSDecorator injectableDecorator;
    private TSClass observableClass;
    private TSClass httpClass;
    private TSClass httpParamsClass;
    private TSClass httpHeadersClass;
    private TSClass urlServiceClass;
    private TSClass subjectClass;
    private Set<TSField> implementationSpecificFieldsSet;

    private boolean useUrlService;

    public Angular4ImplementationGenerator() {
        this(null);
    }

    public Angular4ImplementationGenerator(Path urlServicePath) {
        TSModule angularCoreModule = new TSModule("@angular/core", null, true);
        injectableDecorator = new TSDecorator("", new TSFunction("Injectable", angularCoreModule));

        TSModule subjectModule = new TSModule("rxjs/Subject", null, true);
        TSModule observableModule = new TSModule("rxjs/Observable", null, true);
        observableClass = new TSClass("Observable", observableModule);
        subjectClass = new TSClass("Subject", subjectModule);

        TSModule angularHttpModule = new TSModule("@angular/common/http", null, true);
        httpClass = new TSClass("HttpClient", angularHttpModule);
        httpParamsClass = new TSClass("HttpParams", angularHttpModule);
        httpHeadersClass = new TSClass("HttpHeaders", angularHttpModule);

        useUrlService = urlServicePath != null;
        if (useUrlService) {
            TSModule urlServiceModule = new TSModule("url.service", urlServicePath, false);
            urlServiceClass = new TSClass("UrlService", urlServiceModule);
        }
    }

    @Override
    public void write(BufferedWriter writer, TSMethod method) throws IOException {
        if (method.isConstructor()) {
            for (TSField field : implementationSpecificFieldsSet) {
                writer.write("this." + field.getName() + " = " + field.getName() + ";");
                writer.newLine();
            }
        } else {
            RequestMapping methodRequestMapping = getRequestMapping(method);
            RequestMapping classRequestMapping = getRequestMapping(method.getOwner());

            String tsPath = useUrlService ? "this." + FIELD_NAME_URL_SERVICE + ".getBackendUrl() + '" : "'";
            tsPath += getPathFromRequestMapping(classRequestMapping) + getPathFromRequestMapping(methodRequestMapping) + "'";
            String httpMethod = methodRequestMapping.method()[0].toString();

            writer.write("// path = " + tsPath);
            writer.newLine();
            writer.write("// HTTP method = " + httpMethod);
            writer.newLine();

            String requestBodyVar = "body";
            String requestHeadersVar = "headers";
            String requestParamsVar = "params";

            StringBuilder pathStringBuilder = new StringBuilder(tsPath);
            StringBuilder requestBodyBuilder = new StringBuilder();
            StringBuilder requestParamsBuilder = new StringBuilder();

            readMethodParameters(writer, method, httpMethod, requestParamsVar, pathStringBuilder, requestBodyBuilder, requestParamsBuilder);
            writer.newLine();

            boolean isRequestBodyDefined = !isStringBuilderEmpty(requestBodyBuilder);
            writeRequestOption(writer, requestBodyVar, requestBodyBuilder.toString(), isRequestBodyDefined);

            boolean isRequestParamDefined = !isStringBuilderEmpty(requestParamsBuilder);
            writeRequestOption(writer, requestParamsVar, requestParamsBuilder.toString(), isRequestParamDefined);

            String consumeHeader = getConsumeContentTypeFromRequestMapping(methodRequestMapping);
            boolean isRequestHeaderDefined = !consumeHeader.isEmpty();
            writeRequestOption(writer, requestHeadersVar, consumeHeader, isRequestHeaderDefined);

            TSParameterisedType subjectAnyType = new TSParameterisedType("", subjectClass, TypeMapper.tsAny);
            writer.write("const " + FIELD_NAME_SUBJECT + " = new " + subjectAnyType.getName() + "();");
            writer.newLine();

            String requestOptions = "";
            boolean isMethodProduceTextContent = Arrays.asList(methodRequestMapping.produces()).contains("text/plain");
            requestOptions = composeRequestOptions(requestBodyVar, requestHeadersVar, requestParamsVar, isRequestBodyDefined, isRequestParamDefined, isRequestHeaderDefined, requestOptions, isMethodProduceTextContent);

            tsPath = pathStringBuilder.toString();
            writer.write(
                    "this." + FIELD_NAME_HTTP_SERVICE + ".request("
                            + "'" + httpMethod + "'"
                            + ", " + tsPath
                            + requestOptions
                            + ").subscribe("
                            + "res => " + FIELD_NAME_SUBJECT + getResponseFromRequestMapping(method.getType())
                            + "(err) => {"
                            + FIELD_NAME_SUBJECT + ".error(err ? err : {});});"
            );
            writer.newLine();

            writer.write("return " + FIELD_NAME_SUBJECT + ".asObservable();");
            writer.newLine();

        }

    }

    private void readMethodParameters(BufferedWriter writer, TSMethod method, String httpMethod, String requestParamsVar, StringBuilder pathStringBuilder, StringBuilder requestBodyBuilder, StringBuilder requestParamsBuilder) throws IOException {
        for (TSParameter tsParameter : method.getParameterList()) {
            writer.newLine();
            String tsParameterName = tsParameter.getName();

            if (tsParameter.findAnnotation(RequestBody.class) != null) {
                writer.write(String.format("// parameter %s is sent in request body ", tsParameterName));
                requestBodyBuilder.append(tsParameterName).append(";");
                continue;
            }
            PathVariable pathVariable = tsParameter.findAnnotation(PathVariable.class);
            if (pathVariable != null) {
                String variableName = pathVariable.value();
                if("".equals(variableName)){
                    variableName = tsParameterName;
                }
                writer.write(String.format("// parameter %s is sent in path variable %s ", tsParameterName, variableName));

                String targetToReplace = "{" + variableName + "}";
                if ("id".equals(variableName) && httpMethod.equals("PUT")) {
                    replaceInStringBuilder(pathStringBuilder, targetToReplace, "' + " + tsParameterName + ".id");
                } else {
                    replaceInStringBuilder(pathStringBuilder, targetToReplace, "' + " + tsParameterName + " + '");
                }

                continue;
            }
            RequestParam requestParam = tsParameter.findAnnotation(RequestParam.class);
            if (requestParam != null) {
                String requestParamName = requestParam.value();
                if ("".equals(requestParamName)) {
                    requestParamName = tsParameter.getName();
                }
                writer.write(String.format("// parameter %s is sent as request param %s ", tsParameterName, requestParamName));
                if (isStringBuilderEmpty(requestParamsBuilder)) {
                    requestParamsBuilder.append(" new HttpParams();");
                }
                boolean isNullableType = tsParameter.isNullable();
                if (tsParameter.isOptional() || isNullableType) {
                    requestParamsBuilder
                            .append("\n")
                            .append("if (")
                            .append(tsParameterName)
                            .append(" !== undefined && ")
                            .append(tsParameterName)
                            .append(" !== null) {");
                    addRequestParameter(requestParamsBuilder, requestParamsVar, tsParameter, requestParamName);
                    requestParamsBuilder.append("}");
                } else {
                    addRequestParameter(requestParamsBuilder, requestParamsVar, tsParameter, requestParamName);
                }
            }

        }
    }

    private void addRequestParameter(StringBuilder requestParamsBuilder, String requestParamsVar, TSParameter tsParameter, String requestParamName) {
        String tsParameterName = tsParameter.getName();
        if (!tsParameter.getType().equals(TypeMapper.tsString)) {
            tsParameterName += ".toString()";
        }
        requestParamsBuilder
                .append("\n")
                .append(requestParamsVar)
                .append(" = ")
                .append(requestParamsVar)
                .append(".set('")
                .append(requestParamName)
                .append("',").append(tsParameterName)
                .append(");");
    }

    private boolean isStringBuilderEmpty(StringBuilder requestParamsBuilder) {
        return requestParamsBuilder.length() == 0;
    }

    private void replaceInStringBuilder(StringBuilder pathStringBuilder, String targetToReplace, String replacement) {
        int start = pathStringBuilder.lastIndexOf(targetToReplace);
        int end = start + targetToReplace.length();
        pathStringBuilder.replace(start, end, replacement);
    }

    private void writeRequestOption(BufferedWriter writer, String requestOption, String requestOptionValue, boolean isOptionDefined) throws IOException {
        if (isOptionDefined) {
            writer.write("let " + requestOption + " = " + requestOptionValue);
            writer.newLine();
        }
    }

    private String composeRequestOptions(String requestBodyVar, String requestHeadersVar, String requestParamsVar, boolean isRequestBodyDefined, boolean isRequestParamDefined, boolean isRequestHeaderDefined, String requestOptions, boolean isMethodProduceTextContent) {
        if (isRequestHeaderDefined || isRequestParamDefined || isRequestBodyDefined || isMethodProduceTextContent) {
            List<String> requestOptionsList = new ArrayList<>();
            if (isRequestHeaderDefined) {
                requestOptionsList.add(requestHeadersVar);
            }
            if (isRequestParamDefined) {
                requestOptionsList.add(requestParamsVar);
            }
            if (isRequestBodyDefined) {
                requestOptionsList.add(requestBodyVar);
            }
            if (isMethodProduceTextContent) {
                requestOptionsList.add("responseType: 'text'");
            }

            requestOptions += ", {";
            requestOptions += String.join(", ", requestOptionsList);
            requestOptions += "}";
        }
        return requestOptions;
    }

    private String getPathFromRequestMapping(RequestMapping requestMapping) {
        if (requestMapping.path().length > 0) {
            return requestMapping.path()[0];
        }

        if (requestMapping.value().length > 0) {
            return requestMapping.value()[0];
        }

        return "";
    }

    private String getConsumeContentTypeFromRequestMapping(RequestMapping requestMapping) {
        if (requestMapping.consumes().length > 0) {
            return " new HttpHeaders().set('Content-type'," + " '" + requestMapping.consumes()[0] + "');";
        }
        return "";
    }

    private String getResponseFromRequestMapping(TSType methodType) {
        if (methodType == TypeMapper.tsNumber) {
            return ".next(res ? Number(res) : null),";
        }
        if (methodType == TypeMapper.tsBoolean) {
            return ".next(res ? res.toLowerCase() === 'true' : false),";
        }

        return ".next(res ?  res : null),";
    }

    @Override
    public TSType mapReturnType(TSMethod tsMethod, TSType tsType) {
        if (isRestClass(tsMethod.getOwner())) {
            return new TSParameterisedType("", observableClass, tsType);
        }

        return tsType;
    }

    @Override
    public SortedSet<TSField> getImplementationSpecificFields(TSComplexType tsComplexType) {
        if (isRestClass(tsComplexType)) {
            SortedSet<TSField> fieldsSet = new TreeSet<>();
            fieldsSet.addAll(implementationSpecificFieldsSet);
            return fieldsSet;
        }
        return Collections.emptySortedSet();
    }

    @Override
    public List<TSParameter> getImplementationSpecificParameters(TSMethod method) {
        if (method.isConstructor() && isRestClass(method.getOwner())) {
            List<TSParameter> tsParameters = new ArrayList<>();
            for (TSField field : implementationSpecificFieldsSet) {
                TSParameter newParameter = new TSParameter(field.getName(), field.getType());
                tsParameters.add(newParameter);
            }
            return tsParameters;
        }
        RequestMapping methodRequestMapping = getRequestMapping(method);
        if (methodRequestMapping != null) {
            String methodString = methodRequestMapping.method()[0].toString();
            if ("PUT".equals(methodString) || "POST".equals(methodString)) {
                List<TSParameter> tsParameters = new ArrayList<>();
                return tsParameters;
            }
        }
        return Collections.emptyList();
    }

    private boolean isRestClass(TSComplexType tsComplexType) {
        return tsComplexType.findAnnotation(RequestMapping.class) != null;
    }

    @Override
    public List<TSDecorator> getDecorators(TSMethod tsMethod) {
        return Collections.emptyList();
    }

    @Override
    public List<TSDecorator> getDecorators(TSClass tsClass) {
        return Collections.singletonList(injectableDecorator);
    }

    @Override
    public void addComplexTypeUsage(TSClass tsClass) {
        if (isRestClass(tsClass)) {
            tsClass.addScopedTypeUsage(observableClass);
            tsClass.addScopedTypeUsage(httpClass);
            tsClass.addScopedTypeUsage(httpParamsClass);
            tsClass.addScopedTypeUsage(httpHeadersClass);
            tsClass.addScopedTypeUsage(subjectClass);
            tsClass.addScopedTypeUsage(injectableDecorator.getTsFunction());
            if (useUrlService) {
                tsClass.addScopedTypeUsage(urlServiceClass);
            }
        }
    }

    @Override
    public void addImplementationSpecificFields(TSComplexType tsComplexType) {
        implementationSpecificFieldsSet = new HashSet<>();
        implementationSpecificFieldsSet.add(new TSField(FIELD_NAME_HTTP_SERVICE, tsComplexType, httpClass));
        if (useUrlService) {
            implementationSpecificFieldsSet.add(new TSField(FIELD_NAME_URL_SERVICE, tsComplexType, urlServiceClass));
        }
    }
}