/*
 * Cardinal-Components-API
 * Copyright (C) 2019-2023 OnyxStudios
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package dev.progames723.cca_architectury_port.internal.base.asm;

import dev.progames723.cca_architectury_port.api.v3.component.Component;
import dev.progames723.cca_architectury_port.api.v3.component.ComponentContainer;
import dev.progames723.cca_architectury_port.api.v3.component.ComponentFactory;
import dev.progames723.cca_architectury_port.api.v3.component.ComponentKey;
import dev.progames723.cca_architectury_port.internal.base.CcaEntrypoint;
import dev.progames723.cca_architectury_port.internal.base.ComponentRegistrationInitializer;
import dev.progames723.cca_architectury_port.internal.base.ComponentsInternals;
import dev.progames723.cca_architectury_port.internal.base.LazyDispatcher;
import dev.architectury.event.Event;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public abstract class StaticComponentPluginBase<T, I extends ComponentRegistrationInitializer> extends LazyDispatcher {
    private final ComponentContainer.Factory.Builder<T> containerFactoryBuilder;

    protected StaticComponentPluginBase(String likelyInitTrigger, Class<T> providerClass) {
        super(likelyInitTrigger);
        this.containerFactoryBuilder = ComponentContainer.Factory.builder(providerClass);
    }

    /**
     * Defines an implementation of {@code I} which creates component containers of
     * the given implementation type, using an argument of the given {@code factoryArg} type.
     *
     * <p>The generated class has a single constructor, taking {@code eventCount} parameters of type {@link Event}.
     *
     * @param implNameSuffix       a unique suffix for the generated class
     * @param containerFactoryType the factory interface that is to be implemented by the returned class
     * @param containerImpl        the type of containers that is to be instantiated by the generated factory
     * @param actualFactoryParams  the actual type of the arguments taken by the {@link ComponentContainer} constructor
     */
    public static <I> Class<? extends I> spinContainerFactory(String implNameSuffix, Class<? super I> containerFactoryType, Class<? extends ComponentContainer> containerImpl, List<Class<?>> actualFactoryParams) throws IOException {
        CcaBootstrap.INSTANCE.ensureInitialized();

        CcaAsmHelper.checkValidJavaIdentifier(implNameSuffix);
        Constructor<?>[] constructors = containerImpl.getConstructors();

        if (constructors.length != 1) {
            throw new IllegalStateException("Ambiguous constructor declarations in " + containerImpl + ": " + Arrays.toString(constructors));
        }

        Method factorySam = CcaAsmHelper.findSam(containerFactoryType);

        if (factorySam.getParameterCount() != actualFactoryParams.size()) {
            throw new IllegalArgumentException("Actual argument list length mismatches with factory SAM: " + actualFactoryParams + " and " + factorySam);
        }

        if (constructors[0].getParameterCount() != factorySam.getParameterCount()) {
            throw new IllegalArgumentException("Factory SAM parameter count should be the same as container constructor (found " + factorySam + " for " + constructors[0] + ")");
        }

        Type[] factoryArgs;
        {
            Class<?>[] factoryParamClasses = factorySam.getParameterTypes();
            factoryArgs = new Type[factoryParamClasses.length];

            for (int i = 0; i < factoryParamClasses.length; i++) {
                if (!factoryParamClasses[i].isAssignableFrom(actualFactoryParams.get(i))) {
                    throw new IllegalArgumentException("Container factory parameter %s is not assignable from specified actual parameter %s(%s, %s)".formatted(factoryParamClasses[i].getSimpleName(), actualFactoryParams.get(i).getSimpleName(), factorySam, actualFactoryParams));
                }
                factoryArgs[i] = Type.getType(factoryParamClasses[i]);
            }
        }

        String containerCtorDesc = Type.getConstructorDescriptor(constructors[0]);
        String containerImplName = Type.getInternalName(containerImpl);
        ClassNode containerFactoryWriter = new ClassNode(CcaAsmHelper.ASM_VERSION);
        String factoryImplName = CcaAsmHelper.STATIC_CONTAINER_FACTORY + '_' + implNameSuffix;
        containerFactoryWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, factoryImplName, null, "java/lang/Object", new String[]{Type.getInternalName(containerFactoryType)});
        MethodVisitor init = containerFactoryWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitEnd();
        MethodVisitor createContainer = containerFactoryWriter.visitMethod(Opcodes.ACC_PUBLIC, factorySam.getName(), Type.getMethodDescriptor(factorySam), null, null);
        createContainer.visitTypeInsn(Opcodes.NEW, containerImplName);
        createContainer.visitInsn(Opcodes.DUP);
        // stack: container, container
        for (int i = 0; i < actualFactoryParams.size(); i++) {
            createContainer.visitVarInsn(factoryArgs[i].getOpcode(Opcodes.ILOAD), i + 1);
            if (factoryArgs[i].getSort() == Type.OBJECT || factoryArgs[i].getSort() == Type.ARRAY) {
                createContainer.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(actualFactoryParams.get(i)));
            }
        }
        // stack: container, container, actualFactoryArgs...
        createContainer.visitMethodInsn(Opcodes.INVOKESPECIAL, containerImplName, "<init>", containerCtorDesc, false);
        // stack: container
        createContainer.visitInsn(Opcodes.ARETURN);
        createContainer.visitEnd();
        containerFactoryWriter.visitEnd();
        @SuppressWarnings("unchecked") Class<? extends I> ret = (Class<? extends I>) CcaAsmHelper.generateClass(containerFactoryWriter);
        return ret;
    }

    public static ComponentContainer createEmptyContainer() {
        try {
            Class<? extends ComponentContainer> containerCls = CcaAsmHelper.spinComponentContainer(Runnable.class, Collections.emptyMap(), "Empty");
            return containerCls.getConstructor().newInstance();
        } catch (IOException | ReflectiveOperationException e) {
            throw new StaticComponentLoadingException("Failed to generate empty component container", e);
        }
    }

    protected ComponentContainer.Factory<T> buildContainerFactory() {
        this.ensureInitialized();

        return this.containerFactoryBuilder.build();
    }

    @Override
    protected void init() {
        ComponentsInternals.LOGGER.info("StaticComponentPluginBase#init() call! class: {}", this.getClass());
        processInitializers(this.getEntrypoints(), this::dispatchRegistration);
    }

    public static <I extends ComponentRegistrationInitializer> void processInitializers(Collection<CcaEntrypoint<I>> entrypoints, Consumer<I> action) {
        for (CcaEntrypoint<I> entrypoint : entrypoints) {
            try {
                action.accept(entrypoint.getInstance());
            } catch (Throwable e) {
                throw new StaticComponentLoadingException(String.format("Exception while registering static component factories for %s", entrypoint.getInstance().getClass()), e);
            }
        }
    }

    protected abstract Collection<CcaEntrypoint<I>> getEntrypoints();

    protected abstract void dispatchRegistration(I entrypoint);

    protected <C extends Component> void register(ComponentKey<C> key, ComponentFactory<T, ? extends C> factory) {
        this.containerFactoryBuilder.component(key, factory);
    }

    protected <C extends Component> void register(ComponentKey<? super C> key, Class<C> impl, ComponentFactory<T, ? extends C> factory) {
        this.containerFactoryBuilder.component(key, impl, factory);
    }
}
