package net.legoman.animod.client;

import net.legoman.animod.AniMod;
import net.legoman.animod.AnyaEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class AnyaModel extends GeoModel<AnyaEntity> {
    private static final ResourceLocation HORROR_MODEL =
            new ResourceLocation("animod", "geo/anya_model_horror.geo.json");
    private static final ResourceLocation OFFENDED_MODEL =
            new ResourceLocation("animod", "geo/anya_model_offended.geo.json");
    private static final ResourceLocation TSUNDERE_MODEL =
            new ResourceLocation("animod", "geo/anya_model_tsundere.geo.json");
    private static final ResourceLocation DANDERE_MODEL =
            new ResourceLocation("animod", "geo/anya_model_dandere.geo.json");
    private static final ResourceLocation FRIENDLY_MODEL =
            new ResourceLocation("animod", "geo/anya_model_75.geo.json");
    private static final ResourceLocation YANDERE_MODEL =
            new ResourceLocation("animod", "geo/anya_model_yandere.geo.json");

    private static final ResourceLocation ABUSE_MODEL =
            new ResourceLocation("animod", "geo/anya_model_abuse.geo.json");
    private static final ResourceLocation COMPLIMENT_MODEL =
            new ResourceLocation("animod", "geo/anya_model_compliment.geo.json");

    private static final ResourceLocation HORROR =
            new ResourceLocation("animod", "textures/entity/face_horror.png");
    private static final ResourceLocation OFFENDED =
            new ResourceLocation("animod", "textures/entity/face_offended.png");
    private static final ResourceLocation TSUNDERE =
            new ResourceLocation("animod", "textures/entity/face_tsundere.png");
    private static final ResourceLocation DANDERE =
            new ResourceLocation("animod", "textures/entity/face_dandere.png");
    private static final ResourceLocation FRIENDLY =
            new ResourceLocation("animod", "textures/entity/face_75.png");
    private static final ResourceLocation YANDERE =
            new ResourceLocation("animod", "textures/entity/face_yandere.png");

    private static final ResourceLocation ABUSE =
            new ResourceLocation("animod", "textures/entity/anya_abuse_face.png");
    private static final ResourceLocation COMPLIMENT =
            new ResourceLocation("animod", "textures/entity/anya_compliment_face.png");

    private static final ResourceLocation ANIMATION =
            new ResourceLocation("animod", "animations/anya.animation.json");

    @Override
    public ResourceLocation getModelResource(AnyaEntity anya) {
        if (anya.isReacting()) {
            return anya.getReactionType() == 1 ? ABUSE_MODEL : COMPLIMENT_MODEL;
        }

        int karma = AniMod.getKarma();

        if (karma < 20) return HORROR_MODEL;
        if (karma < 30) return OFFENDED_MODEL;
        if (karma < 50) return TSUNDERE_MODEL;
        if (karma < 75) return DANDERE_MODEL;
        if (karma < 90) return FRIENDLY_MODEL;
        return YANDERE_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(AnyaEntity anya) {
        if (anya.isReacting()) {
            return anya.getReactionType() == 1 ? ABUSE : COMPLIMENT;
        }

        int karma = AniMod.getKarma();

        if (karma < 20) return HORROR;
        if (karma < 30) return OFFENDED;
        if (karma < 50) return TSUNDERE;
        if (karma < 75) return DANDERE;
        if (karma < 90) return FRIENDLY;
        return YANDERE;
    }

    @Override
    public ResourceLocation getAnimationResource(AnyaEntity anya) {
        return ANIMATION;
    }
}