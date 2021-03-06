/**
 * Copyright © 2006-2016 Web Cohesion (info@webcohesion.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webcohesion.enunciate.modules.jackson;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.webcohesion.enunciate.EnunciateContext;
import com.webcohesion.enunciate.EnunciateException;
import com.webcohesion.enunciate.api.InterfaceDescriptionFile;
import com.webcohesion.enunciate.api.datatype.DataType;
import com.webcohesion.enunciate.api.datatype.DataTypeReference;
import com.webcohesion.enunciate.api.datatype.Namespace;
import com.webcohesion.enunciate.api.datatype.Syntax;
import com.webcohesion.enunciate.api.resources.MediaTypeDescriptor;
import com.webcohesion.enunciate.facets.FacetFilter;
import com.webcohesion.enunciate.javac.decorations.type.DecoratedDeclaredType;
import com.webcohesion.enunciate.javac.decorations.type.DecoratedTypeMirror;
import com.webcohesion.enunciate.metadata.json.JsonSeeAlso;
import com.webcohesion.enunciate.metadata.qname.XmlQNameEnum;
import com.webcohesion.enunciate.module.EnunciateModuleContext;
import com.webcohesion.enunciate.modules.jackson.api.impl.DataTypeReferenceImpl;
import com.webcohesion.enunciate.modules.jackson.api.impl.EnumDataTypeImpl;
import com.webcohesion.enunciate.modules.jackson.api.impl.MediaTypeDescriptorImpl;
import com.webcohesion.enunciate.modules.jackson.api.impl.ObjectDataTypeImpl;
import com.webcohesion.enunciate.modules.jackson.model.*;
import com.webcohesion.enunciate.modules.jackson.model.adapters.AdapterType;
import com.webcohesion.enunciate.modules.jackson.model.types.JsonType;
import com.webcohesion.enunciate.modules.jackson.model.types.JsonTypeFactory;
import com.webcohesion.enunciate.modules.jackson.model.types.KnownJsonType;
import com.webcohesion.enunciate.modules.jackson.model.util.JacksonUtil;
import com.webcohesion.enunciate.modules.jackson.model.util.MapType;
import com.webcohesion.enunciate.util.IgnoreUtils;
import com.webcohesion.enunciate.util.OneTimeLogMessage;

import javax.activation.DataHandler;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;
import javax.xml.bind.JAXBElement;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.sql.Timestamp;
import java.util.*;

/**
 * @author Ryan Heaton
 */
@SuppressWarnings ( "unchecked" )
public class EnunciateJacksonContext extends EnunciateModuleContext implements Syntax {

  public static final String SYNTAX_LABEL = "JSON";

  private final Map<String, JsonType> knownTypes;
  private final Map<String, TypeDefinition> typeDefinitions;
  private final boolean honorJaxb;
  private final KnownJsonType dateType;
  private final Map<String, TypeDefinition> typeDefinitionsBySlug;
  private final boolean collapseTypeHierarchy;
  private final Map<String, String> mixins;
  private final boolean disableExamples;

  public EnunciateJacksonContext(EnunciateContext context, boolean honorJaxb, KnownJsonType dateType, boolean collapseTypeHierarchy, Map<String, String> mixins, boolean disableExamples) {
    super(context);
    this.dateType = dateType;
    this.mixins = mixins;
    this.disableExamples = disableExamples;
    this.knownTypes = loadKnownTypes();
    this.typeDefinitions = new HashMap<String, TypeDefinition>();
    this.honorJaxb = honorJaxb;
    this.collapseTypeHierarchy = collapseTypeHierarchy;
    this.typeDefinitionsBySlug = new HashMap<String, TypeDefinition>();
  }

  @Override
  public String getId() {
    return "jackson";
  }

  @Override
  public int compareTo(Syntax syntax) {
    return getId().compareTo(syntax.getId());
  }

  public EnunciateContext getContext() {
    return context;
  }

  public boolean isHonorJaxb() {
    return honorJaxb;
  }

  public boolean isCollapseTypeHierarchy() {
    return collapseTypeHierarchy;
  }

  public Collection<TypeDefinition> getTypeDefinitions() {
    return this.typeDefinitions.values();
  }

  @Override
  public boolean isEmpty() {
    return this.typeDefinitions.isEmpty();
  }

  @Override
  public String getSlug() {
    return "syntax_json";
  }

  @Override
  public String getLabel() {
    return SYNTAX_LABEL;
  }

  public boolean isDisableExamples() {
    return disableExamples;
  }

  @Override
  public boolean isAssignableToMediaType(String mediaType) {
    return mediaType != null && (mediaType.equals("*/*") || mediaType.equals("application/*") || mediaType.endsWith("/json") || mediaType.endsWith("+json"));
  }

  @Override
  public MediaTypeDescriptor findMediaTypeDescriptor(String mediaType, DecoratedTypeMirror typeMirror) {
    if (mediaType == null) {
      return null;
    }

    //if it's a wildcard, we'll return an implicit descriptor.
    if (mediaType.equals("*/*") || mediaType.equals("application/*")) {
      mediaType = "application/json";
    }

    if (mediaType.endsWith("/json") || mediaType.endsWith("+json")) {
      DataTypeReference typeReference = findDataTypeReference(typeMirror);
      return typeReference == null ? null : new MediaTypeDescriptorImpl(mediaType, typeReference);
    }
    else {
      return null;
    }
  }

  private DataTypeReference findDataTypeReference(DecoratedTypeMirror typeMirror) {
    if (typeMirror == null) {
      return null;
    }

    JsonType jsonType;
    try {
      jsonType = JsonTypeFactory.getJsonType(typeMirror, this);
    }
    catch (Exception e) {
      jsonType = null;
    }

    return jsonType == null ? null : new DataTypeReferenceImpl(jsonType);
  }

  @Override
  public List<Namespace> getNamespaces() {
    return Collections.singletonList(getNamespace());
  }

  public Namespace getNamespace() {
    return new JacksonNamespace();
  }

  public JsonType getKnownType(Element declaration) {
    if (declaration instanceof TypeElement) {
      return this.knownTypes.get(((TypeElement) declaration).getQualifiedName().toString());
    }
    return null;
  }

  public TypeDefinition findTypeDefinition(Element declaration) {
    if (declaration instanceof TypeElement) {
      return this.typeDefinitions.get(((TypeElement) declaration).getQualifiedName().toString());
    }
    return null;
  }

  @Override
  public List<DataType> findDataTypes(String name) {
    if (name != null && !name.isEmpty()) {
      TypeElement typeElement = this.context.getProcessingEnvironment().getElementUtils().getTypeElement(name);
      if (typeElement != null) {
        TypeDefinition typeDefinition = findTypeDefinition(typeElement);
        if (typeDefinition instanceof ObjectTypeDefinition) {
          return Collections.singletonList((DataType) new ObjectDataTypeImpl((ObjectTypeDefinition) typeDefinition));
        }
        else if (typeDefinition instanceof EnumTypeDefinition) {
          return Collections.singletonList((DataType) new EnumDataTypeImpl((EnumTypeDefinition) typeDefinition));
        }
      }
    }

    return Collections.emptyList();
  }

  protected Map<String, JsonType> loadKnownTypes() {
    HashMap<String, JsonType> knownTypes = new HashMap<String, JsonType>();

    knownTypes.put(Boolean.class.getName(), KnownJsonType.BOOLEAN);
    knownTypes.put(Byte.class.getName(), KnownJsonType.WHOLE_NUMBER);
    knownTypes.put(Character.class.getName(), KnownJsonType.STRING);
    knownTypes.put(Double.class.getName(), KnownJsonType.NUMBER);
    knownTypes.put(Float.class.getName(), KnownJsonType.NUMBER);
    knownTypes.put(Integer.class.getName(), KnownJsonType.WHOLE_NUMBER);
    knownTypes.put(Long.class.getName(), KnownJsonType.WHOLE_NUMBER);
    knownTypes.put(Short.class.getName(), KnownJsonType.WHOLE_NUMBER);
    knownTypes.put(Boolean.TYPE.getName(), KnownJsonType.BOOLEAN);
    knownTypes.put(Byte.TYPE.getName(), KnownJsonType.WHOLE_NUMBER);
    knownTypes.put(Double.TYPE.getName(), KnownJsonType.NUMBER);
    knownTypes.put(Float.TYPE.getName(), KnownJsonType.NUMBER);
    knownTypes.put(Integer.TYPE.getName(), KnownJsonType.WHOLE_NUMBER);
    knownTypes.put(Long.TYPE.getName(), KnownJsonType.WHOLE_NUMBER);
    knownTypes.put(Short.TYPE.getName(), KnownJsonType.WHOLE_NUMBER);
    knownTypes.put(Character.TYPE.getName(), KnownJsonType.STRING);
    knownTypes.put(String.class.getName(), KnownJsonType.STRING);
    knownTypes.put(Enum.class.getName(), KnownJsonType.STRING);
    knownTypes.put(QName.class.getName(), KnownJsonType.STRING);
    knownTypes.put(java.math.BigInteger.class.getName(), KnownJsonType.WHOLE_NUMBER);
    knownTypes.put(java.math.BigDecimal.class.getName(), KnownJsonType.NUMBER);
    knownTypes.put(java.util.Calendar.class.getName(), this.dateType);
    knownTypes.put(java.util.Date.class.getName(), this.dateType);
    knownTypes.put(Timestamp.class.getName(), this.dateType);
    knownTypes.put(java.net.URI.class.getName(), KnownJsonType.STRING);
    knownTypes.put(java.net.URL.class.getName(), KnownJsonType.STRING);
    knownTypes.put(java.lang.Object.class.getName(), KnownJsonType.OBJECT);
    knownTypes.put(byte[].class.getName(), KnownJsonType.STRING);
    knownTypes.put(DataHandler.class.getName(), KnownJsonType.STRING);
    knownTypes.put(java.util.UUID.class.getName(), KnownJsonType.STRING);
    knownTypes.put(XMLGregorianCalendar.class.getName(), this.dateType);
    knownTypes.put(GregorianCalendar.class.getName(), this.dateType);
    knownTypes.put(JsonNode.class.getName(), KnownJsonType.OBJECT);
    knownTypes.put(ContainerNode.class.getName(), KnownJsonType.OBJECT);
    knownTypes.put(ArrayNode.class.getName(), KnownJsonType.ARRAY);
    knownTypes.put(ObjectNode.class.getName(), KnownJsonType.OBJECT);
    knownTypes.put(ValueNode.class.getName(), KnownJsonType.STRING);
    knownTypes.put(TextNode.class.getName(), KnownJsonType.STRING);
    knownTypes.put(BinaryNode.class.getName(), KnownJsonType.STRING);
    knownTypes.put(MissingNode.class.getName(), KnownJsonType.STRING);
    knownTypes.put(NullNode.class.getName(), KnownJsonType.STRING);
    knownTypes.put(NumericNode.class.getName(), KnownJsonType.WHOLE_NUMBER);
    knownTypes.put(IntNode.class.getName(), KnownJsonType.WHOLE_NUMBER);
    knownTypes.put(ShortNode.class.getName(), KnownJsonType.WHOLE_NUMBER);
    knownTypes.put(FloatNode.class.getName(), KnownJsonType.NUMBER);
    knownTypes.put(DoubleNode.class.getName(), KnownJsonType.NUMBER);
    knownTypes.put(DecimalNode.class.getName(), KnownJsonType.NUMBER);
    knownTypes.put(LongNode.class.getName(), KnownJsonType.WHOLE_NUMBER);
    knownTypes.put(BigIntegerNode.class.getName(), KnownJsonType.WHOLE_NUMBER);
    knownTypes.put(POJONode.class.getName(), KnownJsonType.OBJECT);
    knownTypes.put(BooleanNode.class.getName(), KnownJsonType.BOOLEAN);
    knownTypes.put(Class.class.getName(), KnownJsonType.OBJECT);

    knownTypes.put("java.time.Period", KnownJsonType.STRING);
    knownTypes.put("java.time.Duration", this.dateType);
    knownTypes.put("java.time.Instant", this.dateType);
    knownTypes.put("java.time.Year", this.dateType);
    knownTypes.put("java.time.YearMonth", KnownJsonType.STRING);
    knownTypes.put("java.time.MonthDay", KnownJsonType.STRING);
    knownTypes.put("java.time.ZoneId", KnownJsonType.STRING);
    knownTypes.put("java.time.ZoneOffset", KnownJsonType.STRING);
    knownTypes.put("java.time.LocalDate", KnownJsonType.STRING);
    knownTypes.put("java.time.LocalTime", KnownJsonType.STRING);
    knownTypes.put("java.time.LocalDateTime", KnownJsonType.STRING);
    knownTypes.put("java.time.OffsetTime", KnownJsonType.STRING);
    knownTypes.put("java.time.ZonedDateTime", this.dateType);
    knownTypes.put("java.time.OffsetDateTime", this.dateType);
    knownTypes.put("org.joda.time.DateTime", this.dateType);


    return knownTypes;
  }

  /**
   * Find the type definition for a class given the class's declaration.
   *
   * @param declaration The declaration.
   * @return The type definition.
   */
  protected TypeDefinition createTypeDefinition(TypeElement declaration) {
    if (declaration.getKind() == ElementKind.INTERFACE) {
      if (declaration.getAnnotation(javax.xml.bind.annotation.XmlType.class) != null) {
        throw new EnunciateException(declaration.getQualifiedName() + ": an interface must not be annotated with @XmlType.");
      }
    }

    declaration = narrowToAdaptingType(declaration);

    if (isEnumType(declaration)) {
      if (declaration.getAnnotation(XmlQNameEnum.class) != null) {
        return new QNameEnumTypeDefinition(declaration, this);
      }
      else {
        return new EnumTypeDefinition(declaration, this);
      }
    }
    else {
      ObjectTypeDefinition typeDef = new ObjectTypeDefinition(declaration, this);
      if (typeDef.getValue() != null) {
        return new SimpleTypeDefinition(typeDef);
      }
      else {
        return typeDef;
      }
    }
  }

  /**
   * Narrows the existing declaration down to its adapting declaration, if it's being adapted. Otherwise, the original declaration will be returned.
   *
   * @param declaration The declaration to narrow.
   * @return The narrowed declaration.
   */
  protected TypeElement narrowToAdaptingType(TypeElement declaration) {
    AdapterType adapterType = JacksonUtil.findAdapterType(declaration, this);
    if (adapterType != null) {
      TypeMirror adaptingType = adapterType.getAdaptingType();
      if (adaptingType.getKind() != TypeKind.DECLARED) {
        return declaration;
      }
      else {
        TypeElement adaptingDeclaration = (TypeElement) ((DeclaredType) adaptingType).asElement();
        if (adaptingDeclaration == null) {
          throw new EnunciateException(String.format("Class %s is being adapted by a type (%s) that doesn't seem to be on the classpath.", declaration.getQualifiedName(), adaptingType));
        }
        return adaptingDeclaration;
      }
    }
    return declaration;
  }

  /**
   * A quick check to see if a declaration defines a enum schema type.
   *
   * @param declaration The declaration to check.
   * @return the value of the check.
   */
  protected boolean isEnumType(TypeElement declaration) {
    JsonFormat format = declaration.getAnnotation(JsonFormat.class);
    if (format != null && format.shape() == JsonFormat.Shape.OBJECT) {
      return false;
    }

    return declaration.getKind() == ElementKind.ENUM;
  }

  public boolean isKnownTypeDefinition(TypeElement el) {
    return findTypeDefinition(el) != null || isKnownType(el);
  }

  public boolean isIgnored(Element el) {
    return IgnoreUtils.isIgnored(el) || (el.getAnnotation(JsonIgnore.class) != null && el.getAnnotation(JsonIgnore.class).value());
  }

  public void add(TypeDefinition typeDef, LinkedList<Element> stack) {
    if (findTypeDefinition(typeDef) == null && !isKnownType(typeDef)) {
      this.typeDefinitions.put(typeDef.getQualifiedName().toString(), typeDef);

      if (this.context.isExcluded(typeDef)) {
        warn("Added %s as a Jackson type definition even though is was supposed to be excluded according to configuration. It was referenced from %s%s, so it had to be included to prevent broken references.", typeDef.getQualifiedName(), stack.size() > 0 ? stack.get(0) : "an unknown location", stack.size() > 1 ? " of " + stack.get(1) : "");
      }
      else {
        debug("Added %s as a Jackson type definition.", typeDef.getQualifiedName());
      }

      if (getContext().getProcessingEnvironment().findSourcePosition(typeDef) == null) {
        OneTimeLogMessage.SOURCE_FILES_NOT_FOUND.log(getContext());
        debug("Unable to find source file for %s.", typeDef.getQualifiedName());
      }

      typeDef.getReferencedFrom().addAll(stack);
      try {
        stack.push(typeDef);

        addSeeAlsoTypeDefinitions(typeDef, stack);

        for (Member member : typeDef.getMembers()) {
          addReferencedTypeDefinitions(member, stack);
        }

        Value value = typeDef.getValue();
        if (value != null) {
          addReferencedTypeDefinitions(value, stack);
        }

        TypeMirror superclass = typeDef.getSuperclass();
        if (!typeDef.isBaseObject() && superclass != null && superclass.getKind() != TypeKind.NONE && !isCollapseTypeHierarchy()) {
          addReferencedTypeDefinitions(superclass, stack);
        }
      }
      finally {
        stack.pop();
      }
    }
  }

  protected void addReferencedTypeDefinitions(Accessor accessor, LinkedList<Element> stack) {
    stack.push(accessor);
    try {
      addSeeAlsoTypeDefinitions(accessor, stack);
      TypeMirror enumRef = accessor.getQNameEnumRef();
      if (enumRef != null) {
        addReferencedTypeDefinitions(enumRef, stack);
      }
    }
    finally {
      stack.pop();
    }
  }

  /**
   * Add the type definition(s) referenced by the given value.
   *
   * @param value The value.
   * @param stack The context stack.
   */
  protected void addReferencedTypeDefinitions(Value value, LinkedList<Element> stack) {
    stack.push(value);
    try {
      addReferencedTypeDefinitions((Accessor) value, stack);
      if (value.isAdapted()) {
        addReferencedTypeDefinitions(value.getAdapterType(), stack);
      }
      else if (value.getQNameEnumRef() == null) {
        addReferencedTypeDefinitions(value.getAccessorType(), stack);
      }
    }
    finally {
      stack.pop();
    }
  }

  /**
   * Add the referenced type definitions for the specified element.
   *
   * @param member The element.
   * @param stack  The context stack.
   */
  protected void addReferencedTypeDefinitions(Member member, LinkedList<Element> stack) {
    addReferencedTypeDefinitions((Accessor) member, stack);
    stack.push(member);
    try {
      for (Member choice : member.getChoices()) {
        if (choice.isAdapted()) {
          addReferencedTypeDefinitions(choice.getAdapterType(), stack);
        }
        else if (choice.getQNameEnumRef() == null) {
          addReferencedTypeDefinitions(choice.getAccessorType(), stack);
        }
      }
    }
    finally {
      stack.pop();
    }
  }

  /**
   * Adds any referenced type definitions for the specified type mirror.
   *
   * @param type The type mirror.
   */
  protected void addReferencedTypeDefinitions(TypeMirror type, LinkedList<Element> stack) {
    type.accept(new ReferencedJsonDefinitionVisitor(), new ReferenceContext(stack));
  }

  /**
   * Add any type definitions that are specified as "see also".
   *
   * @param declaration The declaration.
   */
  protected void addSeeAlsoTypeDefinitions(Element declaration, LinkedList<Element> stack) {
    JsonSubTypes subTypes = declaration.getAnnotation(JsonSubTypes.class);
    if (subTypes != null) {
      Elements elementUtils = getContext().getProcessingEnvironment().getElementUtils();
      Types typeUtils = getContext().getProcessingEnvironment().getTypeUtils();
      JsonSubTypes.Type[] types = subTypes.value();
      for (JsonSubTypes.Type type : types) {
        try {
          stack.push(elementUtils.getTypeElement(JsonSubTypes.class.getName()));
          Class clazz = type.value();
          add(createTypeDefinition(elementUtils.getTypeElement(clazz.getName())), stack);
        }
        catch (MirroredTypeException e) {
          TypeMirror mirror = e.getTypeMirror();
          Element element = typeUtils.asElement(mirror);
          if (element instanceof TypeElement) {
            add(createTypeDefinition((TypeElement) element), stack);
          }
        }
        catch (MirroredTypesException e) {
          List<? extends TypeMirror> mirrors = e.getTypeMirrors();
          for (TypeMirror mirror : mirrors) {
            Element element = typeUtils.asElement(mirror);
            if (element instanceof TypeElement) {
              add(createTypeDefinition((TypeElement) element), stack);
            }
          }
        }
        finally {
          stack.pop();
        }
      }
    }

    JsonSeeAlso seeAlso = declaration.getAnnotation(JsonSeeAlso.class);
    if (seeAlso != null) {
      Elements elementUtils = getContext().getProcessingEnvironment().getElementUtils();
      Types typeUtils = getContext().getProcessingEnvironment().getTypeUtils();
      stack.push(elementUtils.getTypeElement(JsonSeeAlso.class.getName()));
      try {
        Class[] classes = seeAlso.value();
        for (Class clazz : classes) {
          add(createTypeDefinition(elementUtils.getTypeElement(clazz.getName())), stack);
        }
      }
      catch (MirroredTypeException e) {
        TypeMirror mirror = e.getTypeMirror();
        Element element = typeUtils.asElement(mirror);
        if (element instanceof TypeElement) {
          add(createTypeDefinition((TypeElement) element), stack);
        }
      }
      catch (MirroredTypesException e) {
        List<? extends TypeMirror> mirrors = e.getTypeMirrors();
        for (TypeMirror mirror : mirrors) {
          Element element = typeUtils.asElement(mirror);
          if (element instanceof TypeElement) {
            add(createTypeDefinition((TypeElement) element), stack);
          }
        }
      }
      finally {
        stack.pop();
      }
    }

    if (subTypes == null && seeAlso == null && declaration instanceof TypeElement) {
      // No annotation tells us what to do, so we'll look up subtypes and add them
      for (Element el : getContext().getApiElements()) {
        if ((el instanceof TypeElement) && !((TypeElement)el).getQualifiedName().contentEquals(((TypeElement)declaration).getQualifiedName()) && ((DecoratedTypeMirror) el.asType()).isInstanceOf(declaration)) {
          add(createTypeDefinition((TypeElement) el), stack);
        }
      }
    }
  }

  /**
   * Whether the specified type is a known type.
   *
   * @param typeDef The type def.
   * @return Whether the specified type is a known type.
   */
  protected boolean isKnownType(TypeElement typeDef) {
    return knownTypes.containsKey(typeDef.getQualifiedName().toString()) || ((DecoratedTypeMirror) typeDef.asType()).isInstanceOf(JAXBElement.class);
  }

  /**
   * Get the slug for the given type definition.
   *
   * @param typeDefinition The type definition.
   * @return The slug for the type definition.
   */
  public String getSlug(TypeDefinition typeDefinition) {
    String[] qualifiedNameTokens = typeDefinition.getQualifiedName().toString().split("\\.");
    String slug = "";
    for (int i = qualifiedNameTokens.length - 1; i >= 0; i--) {
      slug = slug.isEmpty() ? qualifiedNameTokens[i] : slug + "_" + qualifiedNameTokens[i];

      TypeDefinition entry = this.typeDefinitionsBySlug.get(slug);
      if (entry == null) {
        entry = typeDefinition;
        this.typeDefinitionsBySlug.put(slug, entry);
      }

      if (entry.getQualifiedName().toString().equals(typeDefinition.getQualifiedName().toString())) {
        return slug;
      }
    }

    return slug;
  }

  /**
   * Look up the mix-in for a given element.
   *
   * @param element The element for which to look up the mix-in.
   * @return The mixin.
   */
  public TypeElement lookupMixin(TypeElement element) {
    String mixin = this.mixins.get(element.getQualifiedName().toString());
    if (mixin != null) {
      return getContext().getProcessingEnvironment().getElementUtils().getTypeElement(mixin);
    }
    return null;
  }

  /**
   * Visitor for XML-referenced type definitions.
   */
  private class ReferencedJsonDefinitionVisitor extends SimpleTypeVisitor6<Void, ReferenceContext> {

    @Override
    public Void visitArray(ArrayType t, ReferenceContext context) {
      return t.getComponentType().accept(this, context);
    }

    @Override
    public Void visitDeclared(DeclaredType declaredType, ReferenceContext context) {
      TypeElement declaration = (TypeElement) declaredType.asElement();
      if (declaration.getKind() == ElementKind.ENUM) {
        if (!isKnownTypeDefinition(declaration)) {
          add(createTypeDefinition(declaration), context.referenceStack);
        }
      }
      else if (declaredType instanceof AdapterType) {
        ((AdapterType) declaredType).getAdaptingType().accept(this, context);
      }
      else if (MapType.findMapType(declaredType, EnunciateJacksonContext.this) == null) {
        String qualifiedName = declaration.getQualifiedName().toString();
        if (Object.class.getName().equals(qualifiedName)) {
          //skip base object; not a type definition.
          return null;
        }

        if (context.recursionStack.contains(declaration)) {
          //we're already visiting this class...
          return null;
        }

        context.recursionStack.push(declaration);
        try {
          if (!isKnownTypeDefinition(declaration) && !isIgnored(declaration) && declaration.getKind() == ElementKind.CLASS && !((DecoratedDeclaredType) declaredType).isCollection() && !((DecoratedDeclaredType) declaredType).isInstanceOf(JAXBElement.class)) {
            add(createTypeDefinition(declaration), context.referenceStack);
          }

          List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
          if (typeArgs != null) {
            for (TypeMirror typeArg : typeArgs) {
              typeArg.accept(this, context);
            }
          }
        }
        finally {
          context.recursionStack.pop();
        }
      }
      else {
        List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
        if (typeArgs != null) {
          for (TypeMirror typeArg : typeArgs) {
            typeArg.accept(this, context);
          }
        }
      }

      return null;
    }

    @Override
    public Void visitTypeVariable(TypeVariable t, ReferenceContext context) {
      return t.getUpperBound().accept(this, context);
    }

    @Override
    public Void visitWildcard(WildcardType t, ReferenceContext context) {
      TypeMirror extendsBound = t.getExtendsBound();
      if (extendsBound != null) {
        extendsBound.accept(this, context);
      }

      TypeMirror superBound = t.getSuperBound();
      if (superBound != null) {
        superBound.accept(this, context);
      }

      return null;
    }

    @Override
    public Void visitUnknown(TypeMirror t, ReferenceContext context) {
      return defaultAction(t, context);
    }
  }

  private static class ReferenceContext {
    LinkedList<Element> referenceStack;
    LinkedList<Element> recursionStack;

    public ReferenceContext(LinkedList<Element> referenceStack) {
      this.referenceStack = referenceStack;
      recursionStack = new LinkedList<Element>();
    }
  }

  private class JacksonNamespace implements Namespace {
    @Override
    public String getUri() {
      return null; //json has no namespace uri.
    }

    @Override
    public InterfaceDescriptionFile getSchemaFile() {
      return null; //todo: json schema?
    }

    @Override
    public List<? extends DataType> getTypes() {
      Collection<TypeDefinition> typeDefinitions = getTypeDefinitions();
      ArrayList<DataType> dataTypes = new ArrayList<DataType>();
      FacetFilter facetFilter = getContext().getConfiguration().getFacetFilter();
      for (TypeDefinition typeDefinition : typeDefinitions) {
        if (!facetFilter.accept(typeDefinition)) {
          continue;
        }

        if (typeDefinition instanceof ObjectTypeDefinition) {
          dataTypes.add(new ObjectDataTypeImpl((ObjectTypeDefinition) typeDefinition));
        }
        else if (typeDefinition instanceof EnumTypeDefinition) {
          dataTypes.add(new EnumDataTypeImpl((EnumTypeDefinition) typeDefinition));
        }
      }

      Collections.sort(dataTypes, new Comparator<DataType>() {
        @Override
        public int compare(DataType o1, DataType o2) {
          return o1.getLabel().compareTo(o2.getLabel());
        }
      });

      return dataTypes;
    }

  }
}
