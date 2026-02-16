package com.apocscode.logiclink.client;

import com.apocscode.logiclink.entity.RemoteSeatEntity;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Invisible renderer for the RemoteSeatEntity.
 * Renders nothing — the entity is purely functional (holds the seated player).
 */
public class RemoteSeatRenderer extends EntityRenderer<RemoteSeatEntity> {

    public RemoteSeatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(RemoteSeatEntity entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(RemoteSeatEntity entity) {
        return null;
    }
}
