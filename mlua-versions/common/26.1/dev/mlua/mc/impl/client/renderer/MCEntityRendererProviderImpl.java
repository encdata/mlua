package dev.mlua.mc.impl.client.renderer;
import dev.mlua.mcapi.client.renderer.MCEntityRenderer;
import dev.mlua.mcapi.client.renderer.MCEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
public class MCEntityRendererProviderImpl implements MCEntityRendererProvider {
    private final EntityRendererProvider<?> inner;
    public MCEntityRendererProviderImpl(EntityRendererProvider<?> inner) { this.inner = inner; }
    public static MCEntityRendererProviderImpl fromReal(EntityRendererProvider<?> inner) { return inner == null ? null : new MCEntityRendererProviderImpl(inner); }
    public EntityRendererProvider<?> inner() { return inner; }
    @Override public MCEntityRenderer create(Object manager) { return manager instanceof EntityRendererProvider.Context context ? MCEntityRendererImpl.fromReal(inner.create(context)) : null; }
}
