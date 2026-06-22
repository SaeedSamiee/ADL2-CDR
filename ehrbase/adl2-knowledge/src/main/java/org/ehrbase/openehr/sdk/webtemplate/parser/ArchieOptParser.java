/*
 * Copyright (c) 2026 ADL2 CDR Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package org.ehrbase.openehr.sdk.webtemplate.parser;

import com.nedap.archie.aom.ArchetypeSlot;
import com.nedap.archie.aom.primitives.CString;
import com.nedap.archie.rules.Assertion;
import com.nedap.archie.rules.BinaryOperator;
import com.nedap.archie.rules.Constraint;
import com.nedap.archie.aom.CArchetypeRoot;
import com.nedap.archie.aom.CAttribute;
import com.nedap.archie.aom.CComplexObject;
import com.nedap.archie.aom.CObject;
import com.nedap.archie.aom.CPrimitiveObject;
import com.nedap.archie.aom.OperationalTemplate;
import com.nedap.archie.aom.terminology.ArchetypeTerm;
import com.nedap.archie.aom.terminology.ArchetypeTerminology;
import com.nedap.archie.base.Cardinality;
import com.nedap.archie.base.MultiplicityInterval;
import com.nedap.archie.rm.archetyped.Locatable;
import com.nedap.archie.rm.archetyped.Pathable;
import com.nedap.archie.rm.composition.Action;
import com.nedap.archie.rm.composition.Activity;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.Entry;
import com.nedap.archie.rm.composition.EventContext;
import com.nedap.archie.rm.composition.Instruction;
import com.nedap.archie.rm.composition.IsmTransition;
import com.nedap.archie.rm.datastructures.Element;
import com.nedap.archie.rm.datastructures.Event;
import com.nedap.archie.rm.datastructures.History;
import com.nedap.archie.rm.datavalues.quantity.DvInterval;
import com.nedap.archie.rminfo.ArchieRMInfoLookup;
import com.nedap.archie.rminfo.RMAttributeInfo;
import com.nedap.archie.rminfo.RMTypeInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.aql.webtemplatepath.AqlPath;
import org.ehrbase.openehr.sdk.terminology.TermDefinition;
import org.ehrbase.openehr.sdk.util.rmconstants.RmConstants;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplateAnnotation;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplateInput;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplateNode;
import org.ehrbase.openehr.sdk.webtemplate.model.WebtemplateCardinality;
import org.ehrbase.openehr.sdk.webtemplate.util.WebTemplateUtils;

/**
 * Builds SDK {@link WebTemplate} trees from Archie ADL2 {@link OperationalTemplate} constraints,
 * mirroring {@link OPTParser} structure for AOM 2.x types.
 */
public class ArchieOptParser {

    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{IsAlphabetic}0-9._\\-]");
    private static final Set<String> LOCATABLE_TYPES =
            ArchieRMInfoLookup.getInstance().getTypeInfo(Locatable.class).getAllDescendantClasses().stream()
                    .map(RMTypeInfo::getRmName)
                    .collect(Collectors.toSet());

    private final OperationalTemplate operationalTemplate;
    private final String defaultLanguage;
    private final InputHandler inputHandler;
    private final Map<String, String> choiceIdCache = new HashMap<>();

    public ArchieOptParser(OperationalTemplate operationalTemplate, String defaultLanguage) {
        this.operationalTemplate = operationalTemplate;
        this.defaultLanguage = defaultLanguage == null ? "en" : defaultLanguage;
        this.inputHandler = new InputHandler(Collections.emptyMap());
    }

    public WebTemplate parse() {
        WebTemplate webTemplate = new WebTemplate();
        if (operationalTemplate.getArchetypeId() != null) {
            webTemplate.setTemplateId(operationalTemplate.getArchetypeId().getFullId());
            webTemplate.setVersion(operationalTemplate.getArchetypeId().getVersionId());
        }
        webTemplate.setDefaultLanguage(defaultLanguage);

        CComplexObject definition = operationalTemplate.getDefinition();
        if (definition == null) {
            throw new IllegalArgumentException("ADL2 template has no definition");
        }

        Map<String, Map<String, TermDefinition>> termDefinitionMap = buildTermDefinitionMap();
        WebTemplateNode root = parseRoot(definition, termDefinitionMap);
        webTemplate.setTree(root);
        return webTemplate;
    }

    private WebTemplateNode parseRoot(CComplexObject definition, Map<String, Map<String, TermDefinition>> termMap) {
        WebTemplateNode[] nodes = parseCComplexObject(definition, AqlPath.EMPTY_PATH, termMap, null);
        WebTemplateNode root = nodes[0];
        if (operationalTemplate.getArchetypeId() != null) {
            root.setNodeId(operationalTemplate.getArchetypeId().getFullId());
        }
        addRMAttributes(root, AqlPath.EMPTY_PATH, termMap);
        return root;
    }

    private Map<String, Map<String, TermDefinition>> buildTermDefinitionMap() {
        Map<String, Map<String, TermDefinition>> result = new HashMap<>();
        mergeTerminology(result, operationalTemplate.getTerminology());
        if (operationalTemplate.getComponentTerminologies() != null) {
            operationalTemplate.getComponentTerminologies().values().forEach(t -> mergeTerminology(result, t));
        }
        if (operationalTemplate.getTerminologyExtracts() != null) {
            operationalTemplate.getTerminologyExtracts().values().forEach(t -> mergeTerminology(result, t));
        }
        return result;
    }

    private static void mergeTerminology(
            Map<String, Map<String, TermDefinition>> target, ArchetypeTerminology terminology) {
        if (terminology == null || terminology.getTermDefinitions() == null) {
            return;
        }
        terminology.getTermDefinitions().forEach((language, terms) -> terms.forEach((code, term) -> target
                .computeIfAbsent(code, c -> new HashMap<>())
                .put(language, toTermDefinition(term))));
    }

    private static TermDefinition toTermDefinition(ArchetypeTerm term) {
        Map<String, String> other = new HashMap<>();
        term.forEach((key, value) -> {
            if (!"text".equals(key) && !"description".equals(key)) {
                other.put(key, value);
            }
        });
        return new TermDefinition(term.getCode(), term.getText(), term.getDescription(), other);
    }

    private WebTemplateNode[] parseCComplexObject(
            CComplexObject ccomplexobject,
            AqlPath aqlPath,
            Map<String, Map<String, TermDefinition>> termDefinitionMap,
            String rmAttributeName) {
        WebTemplateNode node = buildNodeWithName(ccomplexobject, aqlPath, termDefinitionMap, rmAttributeName);
        parseComplexObjectSingle(ccomplexobject, node.getAqlPathDto(), termDefinitionMap, node, rmAttributeName);
        return new WebTemplateNode[] {node};
    }

    private WebTemplateNode buildNodeWithName(
            CComplexObject ccomplexobject,
            AqlPath aqlPath,
            Map<String, Map<String, TermDefinition>> termDefinitionMap,
            String rmAttributeName) {
        WebTemplateNode node = buildNode(ccomplexobject, rmAttributeName, termDefinitionMap);
        node.setAqlPath(aqlPath);
        return node;
    }

    private void parseComplexObjectSingle(
            CComplexObject ccomplexobject,
            AqlPath aqlPath,
            Map<String, Map<String, TermDefinition>> termDefinitionMap,
            WebTemplateNode node,
            String rmAttributeName) {
        Map<String, WebTemplateInput> inputMap = new HashMap<>();
        List<org.apache.commons.lang3.tuple.Pair<WebtemplateCardinality, List<AqlPath>>> cardinalityList =
                new ArrayList<>();

        if (ccomplexobject.getAttributes() == null) {
            finishNode(node, aqlPath, termDefinitionMap, inputMap, cardinalityList, rmAttributeName);
            return;
        }

        for (CAttribute cattribute : ccomplexobject.getAttributes()) {
            if (cattribute.getRmAttributeName() == null) {
                continue;
            }
            AqlPath pathLoop = aqlPath.addEnd(cattribute.getRmAttributeName());
            if ("name".equals(pathLoop.getLastNode().getName())) {
                continue;
            }

            List<WebTemplateNode[]> newChildren = new ArrayList<>();
            if (cattribute.getChildren() != null) {
                for (CObject child : cattribute.getChildren()) {
                    if (child instanceof CPrimitiveObject<?, ?> primitive) {
                        inputMap.put(cattribute.getRmAttributeName(), extractPrimitiveInput(primitive));
                    } else {
                        WebTemplateNode[] childNodes =
                                parseCObject(child, pathLoop, termDefinitionMap, cattribute.getRmAttributeName());
                        newChildren.add(childNodes);
                    }
                }
            }

            if (!cattribute.isMultiple()
                    && cattribute.getExistence() != null
                    && cattribute.getExistence().getLower() != null
                    && cattribute.getExistence().getLower() == 0) {
                WebtemplateCardinality cardinality = new WebtemplateCardinality();
                cardinality.setMin(cattribute.getExistence().getLower());
                if (cattribute.getExistence().isUpperUnbounded()) {
                    cardinality.setMax(-1);
                } else if (cattribute.getExistence().getUpper() != null) {
                    cardinality.setMax(cattribute.getExistence().getUpper());
                }
                cardinality.setExcludeFromWebTemplate(Boolean.TRUE);
                newChildren.forEach(cs -> {
                    for (WebTemplateNode c : cs) {
                        cardinality.getIds().add(c.getId());
                    }
                });
                node.getCardinalities().add(cardinality);
            }

            if (cattribute.isMultiple() && cattribute.getCardinality() != null) {
                WebtemplateCardinality webtemplateCardinality = buildCardinality(cattribute.getCardinality());
                List<AqlPath> paths = newChildren.stream()
                        .flatMap(Arrays::stream)
                        .map(WebTemplateNode::getAqlPathDto)
                        .collect(Collectors.toList());
                cardinalityList.add(org.apache.commons.lang3.tuple.Pair.of(webtemplateCardinality, paths));
            }

            newChildren.forEach(cs -> node.getChildren().addAll(Arrays.asList(cs)));
        }

        finishNode(node, aqlPath, termDefinitionMap, inputMap, cardinalityList, rmAttributeName);
    }

    private void finishNode(
            WebTemplateNode node,
            AqlPath aqlPath,
            Map<String, Map<String, TermDefinition>> termDefinitionMap,
            Map<String, WebTemplateInput> inputMap,
            List<org.apache.commons.lang3.tuple.Pair<WebtemplateCardinality, List<AqlPath>>> cardinalityList,
            String rmAttributeName) {
        node.getChoicesInChildren().values().stream()
                .flatMap(List::stream)
                .forEach(this::updateChoiceId);

        if (RmConstants.ELEMENT.equals(node.getRmType())) {
            if (node.getChildren().isEmpty()) {
                Stream.of(
                                RmConstants.DV_TEXT,
                                RmConstants.DV_CODED_TEXT,
                                RmConstants.DV_MULTIMEDIA,
                                RmConstants.DV_PARSABLE,
                                RmConstants.DV_STATE,
                                RmConstants.DV_BOOLEAN,
                                RmConstants.DV_IDENTIFIER,
                                RmConstants.DV_URI,
                                RmConstants.DV_EHR_URI,
                                RmConstants.DV_DURATION,
                                RmConstants.DV_QUANTITY,
                                RmConstants.DV_COUNT,
                                RmConstants.DV_PROPORTION,
                                RmConstants.DV_DATE_TIME,
                                RmConstants.DV_TIME,
                                RmConstants.DV_ORDINAL,
                                RmConstants.DV_DATE)
                        .forEach(t -> addAnyNode(node, t, inputMap));
            } else {
                List<WebTemplateNode> trueChildren = WebTemplateUtils.getTrueChildrenElement(node);
                trueChildren.forEach(c -> pushProperties(node, c));
                if (trueChildren.size() != 1 && node.getChoicesInChildren().isEmpty()) {
                    WebTemplateUtils.getTrueChildrenElement(node).stream()
                            .filter(this::updateChoiceId)
                            .forEach(n -> {
                                n.getLocalizedDescriptions().putAll(node.getLocalizedDescriptions());
                                n.getLocalizedNames().putAll(node.getLocalizedNames());
                            });
                }
            }
        }

        if (RmConstants.DV_CODED_TEXT.equals(node.getRmType())) {
            Optional<WebTemplateNode> matching = node.streamMatching(
                            n -> RmConstants.CODE_PHRASE.equals(n.getRmType()))
                    .findFirst();
            List<WebTemplateInput> inputs = node.getInputs();
            if (matching.isEmpty()) {
                inputs.add(InputHandler.buildWebTemplateInput("value", "TEXT"));
                inputs.add(InputHandler.buildWebTemplateInput("code", "TEXT"));
            } else {
                inputs.addAll(matching.get().getInputs());
            }
        }

        addRMAttributes(node, aqlPath, termDefinitionMap);

        if (node.getInputs().isEmpty()) {
            inputHandler.addInputs(node, inputMap);
        }

        makeIdUnique(node);

        if ("content".equals(rmAttributeName)) {
            applyWrapperContentArchetypeId(node);
        }

        cardinalityList.forEach(p -> {
            WebtemplateCardinality key = p.getKey();
            boolean nonTrivialMin = (key.getMin() != null && key.getMin() > 1);
            int max = key.getMax() == null ? -1 : key.getMax();

            if (nonTrivialMin || max != -1) {
                String[] nodeIds = p.getValue().stream()
                        .flatMap(s -> node.streamMatching(n -> n.getAqlPathDto().equals(s)))
                        .map(WebTemplateNode::getId)
                        .toArray(String[]::new);
                if (nonTrivialMin || max < nodeIds.length) {
                    key.getIds().addAll(Arrays.asList(nodeIds));
                    node.getCardinalities().add(key);
                }
            }
        });

        node.getChildren().forEach(child -> addInContext(node, child));
    }

    private WebTemplateNode[] parseCObject(
            CObject cobject,
            AqlPath aqlPath,
            Map<String, Map<String, TermDefinition>> termDefinitionMap,
            String rmAttributeName) {

        if (cobject instanceof CArchetypeRoot carchetyperoot) {
            String nodeId = carchetyperoot.getArchetypeRef();
            AqlPath pathLoop = StringUtils.isNotBlank(nodeId)
                    ? aqlPath.replaceLastNode(n -> n.withAtCode(nodeId))
                    : aqlPath;
            return parseCComplexObject(carchetyperoot, pathLoop, termDefinitionMap, rmAttributeName);
        }

        if (cobject instanceof CComplexObject ccomplexobject) {
            String nodeId = isLocatableNode(ccomplexobject) ? ccomplexobject.getNodeId() : null;
            AqlPath pathLoop = StringUtils.isNotBlank(nodeId)
                    ? aqlPath.replaceLastNode(n -> n.withAtCode(nodeId))
                    : aqlPath;
            return parseCComplexObject(ccomplexobject, pathLoop, termDefinitionMap, rmAttributeName);
        }

        if (cobject instanceof ArchetypeSlot slot) {
            String localNodeId = slot.getNodeId();
            AqlPath pathLoop = StringUtils.isNotBlank(localNodeId)
                    ? aqlPath.replaceLastNode(n -> n.withAtCode(localNodeId))
                    : aqlPath;
            WebTemplateNode node = buildNode(slot, rmAttributeName, termDefinitionMap);
            node.setAqlPath(pathLoop);
            if (isInstantiableRmType(slot.getRmTypeName())) {
                addRMAttributes(node, pathLoop, termDefinitionMap);
                makeIdUnique(node);
            } else {
                node.setArchetypeSlot(true);
            }
            return new WebTemplateNode[] {node};
        }

        if ("DV_SCALE".equals(cobject.getRmTypeName())) {
            throw new IllegalArgumentException("The supplied template is not supported: Unsupported type DV_SCALE.");
        }

        return new WebTemplateNode[] {};
    }

    private WebTemplateNode buildNode(
            CObject cobject, String rmAttributeName, Map<String, Map<String, TermDefinition>> termDefinitionMap) {
        WebTemplateNode node = new WebTemplateNode();
        node.setRmType(cobject.getRmTypeName());
        applyOccurrences(node, cobject.getOccurrences());

        String nodeId = isLocatableNode(cobject) ? cobject.getNodeId() : null;
        if (StringUtils.isNotBlank(nodeId)) {
            String name = resolveTermText(termDefinitionMap, nodeId, cobject.getMeaning());
            node.setName(name);
            node.setId(buildId(name));
            node.setNodeId(nodeId);
            node.setLocalizedName(name);

            Map<String, TermDefinition> terms = termDefinitionMap.get(nodeId);
            if (terms != null) {
                node.getLocalizedNames()
                        .putAll(terms.entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue())));
                node.getLocalizedNames().put(defaultLanguage, name);
                node.getLocalizedDescriptions()
                        .putAll(terms.entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> Optional.ofNullable(e.getValue().getDescription())
                                                .orElse(e.getValue().getValue()))));
                Optional.ofNullable(terms.get(defaultLanguage))
                        .map(TermDefinition::getOther)
                        .ifPresent(other -> other.forEach((key, value) -> {
                            if (node.getAnnotations() == null) {
                                node.setAnnotations(new WebTemplateAnnotation());
                            }
                            if ("comment".equals(key)) {
                                node.getAnnotations().setComment(value);
                            } else {
                                node.getAnnotations().getOther().put(key, value);
                            }
                        }));
            }
        } else {
            String name = StringUtils.isNotBlank(rmAttributeName) ? rmAttributeName : cobject.getRmTypeName();
            node.setId(buildId(name));
            node.setName(name);
            node.setLocalizedName(name);
        }
        return node;
    }

    private String resolveTermText(
            Map<String, Map<String, TermDefinition>> termDefinitionMap, String nodeId, String meaning) {
        if (StringUtils.isNotBlank(meaning)) {
            return meaning;
        }
        Map<String, TermDefinition> terms = termDefinitionMap.get(nodeId);
        if (terms != null && terms.get(defaultLanguage) != null) {
            return terms.get(defaultLanguage).getValue();
        }
        return nodeId;
    }

    private static void applyOccurrences(WebTemplateNode node, MultiplicityInterval occurrences) {
        if (occurrences == null) {
            node.setMin(0);
            node.setMax(-1);
            return;
        }
        if (occurrences.getLower() == null || occurrences.isLowerUnbounded()) {
            node.setMin(-1);
        } else {
            node.setMin(occurrences.getLower());
        }
        if (occurrences.isUpperUnbounded()) {
            node.setMax(-1);
        } else if (occurrences.getUpper() == null) {
            node.setMax(-1);
        } else {
            node.setMax(occurrences.getUpper());
        }
    }

    private void addRMAttributes(
            WebTemplateNode node, AqlPath aqlPath, Map<String, Map<String, TermDefinition>> termDefinitionMap) {
        RMTypeInfo typeInfo = ArchieRMInfoLookup.getInstance().getTypeInfo(node.getRmType());
        if (typeInfo == null) {
            return;
        }
        Class<?> javaClass = typeInfo.getJavaClass();
        if (!Pathable.class.isAssignableFrom(javaClass) && !DvInterval.class.isAssignableFrom(javaClass)) {
            return;
        }

        List<WebTemplateNode> children = node.getChildren();
        typeInfo.getAttributes().values().stream()
                .filter(s -> {
                    String rmName = s.getRmName();
                    return !(s.isComputed()
                            || "value".equals(rmName)
                            || (Event.class.isAssignableFrom(javaClass) && "offset".equals(rmName))
                            || (Element.class.isAssignableFrom(javaClass)
                                    && switch (rmName) {
                                        case "name", "feeder_audit", "null_flavour" -> false;
                                        default -> true;
                                    })
                            || Locatable.class.isAssignableFrom(s.getTypeInCollection())
                            || (DvInterval.class.isAssignableFrom(javaClass) && "interval".equals(rmName)));
                })
                .map(i -> {
                    String id = buildId(i.getRmName());
                    for (WebTemplateNode child : children) {
                        if (child.getId().equals(id)) {
                            return null;
                        }
                    }
                    return buildNodeForAttribute(i, aqlPath, termDefinitionMap);
                })
                .filter(Objects::nonNull)
                .forEach(children::add);
    }

    private WebTemplateNode buildNodeForAttribute(
            RMAttributeInfo attributeInfo,
            AqlPath aqlPath,
            Map<String, Map<String, TermDefinition>> termDefinitionMap) {
        WebTemplateNode node = new WebTemplateNode();
        String rmName = attributeInfo.getRmName();
        node.setAqlPath(aqlPath.addEnd(rmName));
        node.setName(rmName);
        node.setId(buildId(rmName));
        node.setRmType(attributeInfo.getTypeNameInCollection());
        node.setMax(attributeInfo.isMultipleValued() ? -1 : 1);
        node.setMin(attributeInfo.isNullable() ? 0 : 1);
        if ("action_archetype_id".equals(rmName) || "math_function".equals(rmName)) {
            node.setMin(1);
        }
        inputHandler.addInputs(node, Collections.emptyMap());
        addRMAttributes(node, node.getAqlPathDto(), termDefinitionMap);
        return node;
    }

    private static WebtemplateCardinality buildCardinality(Cardinality cardinality) {
        WebtemplateCardinality webtemplateCardinality = new WebtemplateCardinality();
        if (cardinality.getInterval() != null) {
            if (!cardinality.getInterval().isLowerUnbounded() && cardinality.getInterval().getLower() != null) {
                webtemplateCardinality.setMin(cardinality.getInterval().getLower());
            }
            if (cardinality.getInterval().isUpperUnbounded()) {
                webtemplateCardinality.setMax(-1);
            } else if (cardinality.getInterval().getUpper() != null) {
                webtemplateCardinality.setMax(cardinality.getInterval().getUpper());
            }
        }
        return webtemplateCardinality;
    }

    private void addAnyNode(WebTemplateNode node, String rmType, Map<String, WebTemplateInput> inputMap) {
        WebTemplateNode subNode = new WebTemplateNode();
        subNode.setRmType(rmType);
        updateChoiceId(subNode);
        subNode.setName(node.getName());
        subNode.setAqlPath(node.getAqlPathDto().addEnd("value"));
        subNode.setInContext(true);
        subNode.setMax(1);
        subNode.setMin(0);
        subNode.setLocalizedName(node.getLocalizedName());
        subNode.getLocalizedDescriptions().putAll(node.getLocalizedDescriptions());
        subNode.getLocalizedNames().putAll(node.getLocalizedNames());
        inputHandler.addInputs(subNode, inputMap);
        node.getChildren().add(subNode);
    }

    private void pushProperties(WebTemplateNode node, WebTemplateNode value) {
        value.setNodeId(node.getNodeId());
        value.setAnnotations(node.getAnnotations());
        value.setName(node.getName());
        value.getLocalizedDescriptions().putAll(node.getLocalizedDescriptions());
        value.getLocalizedNames().putAll(node.getLocalizedNames());
        value.setLocalizedName(node.getLocalizedName());
    }

    private void addInContext(WebTemplateNode node, WebTemplateNode child) {
        Map<Class<?>, List<String>> contextAttributes = Map.of(
                Locatable.class, List.of("language"),
                Action.class, List.of("time"),
                Activity.class, List.of("timing", "action_archetype_id"),
                Instruction.class, List.of("narrative"),
                IsmTransition.class, List.of("current_state", "careflow_step", "transition"),
                History.class, List.of("origin"),
                Event.class, List.of("time"),
                Entry.class, List.of("language", "provider", "other_participations", "subject", "encoding"),
                EventContext.class,
                        List.of("start_time", "end_time", "location", "setting", "healthCareFacility", "participations"),
                Composition.class, List.of("language", "territory", "composer", "category"));

        RMTypeInfo typeInfo = ArchieRMInfoLookup.getInstance().getTypeInfo(node.getRmType());
        if (typeInfo != null) {
            contextAttributes.forEach((k, v) -> {
                if (k.isAssignableFrom(typeInfo.getJavaClass()) && v.contains(child.getId())) {
                    child.setInContext(true);
                }
            });
        }
    }

    private boolean updateChoiceId(WebTemplateNode node) {
        String rmType = node.getRmType();
        if (rmType != null && rmType.startsWith("DV_")) {
            node.setId(choiceIdCache.computeIfAbsent(rmType, t -> t.substring(3).toLowerCase() + "_value"));
            return true;
        }
        return false;
    }

    private static boolean isLocatableNode(CObject cobject) {
        return LOCATABLE_TYPES.contains(cobject.getRmTypeName());
    }

    private static boolean isInstantiableRmType(String rmTypeName) {
        RMTypeInfo typeInfo = ArchieRMInfoLookup.getInstance().getTypeInfo(rmTypeName);
        if (typeInfo == null) {
            return false;
        }
        Class<?> javaClass = typeInfo.getJavaClass();
        if (javaClass == null || javaClass.isInterface()) {
            return false;
        }
        return !java.lang.reflect.Modifier.isAbstract(javaClass.getModifiers());
    }

    private void applyWrapperContentArchetypeId(WebTemplateNode node) {
        if (!"OBSERVATION".equals(node.getRmType()) || operationalTemplate.getArchetypeId() == null) {
            return;
        }
        String wrapperId = operationalTemplate.getArchetypeId().getFullId();
        String observationArchetypeId =
                switch (wrapperId) {
                    case "openEHR-EHR-COMPOSITION.with_rules_wrapper.v1.0.0" ->
                            "openEHR-EHR-OBSERVATION.with_rules.v1.0.0";
                    case "openEHR-EHR-COMPOSITION.specialized_observation_wrapper.v1.0.0" ->
                            "openEHR-EHR-OBSERVATION.specialized_template_observation.v1.0.0";
                    default -> null;
                };
        if (observationArchetypeId == null) {
            return;
        }
        node.setNodeId(observationArchetypeId);
        node.setAqlPath(node.getAqlPathDto().replaceLastNode(n -> n.withAtCode(observationArchetypeId)));
    }

    public static String resolveSlotArchetypeNodeId(ArchetypeSlot slot) {
        if (slot.getIncludes() == null || slot.getIncludes().isEmpty()) {
            return null;
        }
        try {
            Assertion assertion = slot.getIncludes().get(0);
            if (!(assertion.getExpression() instanceof BinaryOperator matches)) {
                return null;
            }
            if (!(matches.getRightOperand() instanceof Constraint<?> constraint)) {
                return null;
            }
            if (!(constraint.getItem() instanceof CString cString) || cString.getConstraint().isEmpty()) {
                return null;
            }
            String pattern = cString.getConstraint().get(0);
            if (pattern.startsWith("/") && pattern.endsWith("/")) {
                pattern = pattern.substring(1, pattern.length() - 1);
            }
            pattern = pattern.replace("\\.", ".");
            int regexSuffix = pattern.indexOf(".*");
            if (regexSuffix > 0) {
                pattern = pattern.substring(0, regexSuffix);
            }
            int optionalSuffix = pattern.indexOf("(-");
            if (optionalSuffix > 0) {
                pattern = pattern.substring(0, optionalSuffix);
            }
            return pattern;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private WebTemplateInput extractPrimitiveInput(CPrimitiveObject<?, ?> primitive) {
        WebTemplateInput input = InputHandler.buildWebTemplateInput("value", "TEXT");
        Object assumed = primitive.getAssumedValue();
        if (assumed != null) {
            input.setDefaultValue(String.valueOf(assumed));
        }
        return input;
    }

    public static void makeIdUnique(WebTemplateNode node) {
        node.getChildren().stream()
                .collect(Collectors.groupingBy(n -> n.getId(false)))
                .values()
                .forEach(l -> {
                    if (l.size() > 1) {
                        for (int i = 1; i < l.size(); i++) {
                            WebTemplateNode n = l.get(i);
                            int optionalIdNumber = i + 1;
                            n.setOptionalIdNumber(optionalIdNumber);
                            if (RmConstants.ELEMENT.equals(n.getRmType())) {
                                n.getChildren().stream()
                                        .filter(c -> c.getId().equals(n.getId(false)))
                                        .forEach(c -> c.setOptionalIdNumber(optionalIdNumber));
                            }
                        }
                    } else if (!l.isEmpty()) {
                        l.get(0).setOptionalIdNumber(null);
                    }
                });
    }

    public static String buildId(String term) {
        String alnumsCleaned = NON_ALNUM.matcher(term.toLowerCase()).replaceAll(" ");
        String normalTerm = StringUtils.normalizeSpace(alnumsCleaned).replace(' ', '_');
        if (normalTerm.isEmpty()) {
            return "node";
        }
        if (Character.isDigit(normalTerm.charAt(0))) {
            return "a" + normalTerm;
        }
        return normalTerm;
    }

}
