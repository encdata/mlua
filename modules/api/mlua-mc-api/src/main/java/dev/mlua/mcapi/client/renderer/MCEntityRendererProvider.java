package dev.mlua.mcapi.client.renderer;

@FunctionalInterface
public interface MCEntityRendererProvider {
    MCEntityRenderer create(Object entityRendererManager);
}
