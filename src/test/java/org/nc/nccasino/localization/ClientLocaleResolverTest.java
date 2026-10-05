package org.nc.nccasino.localization;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientLocaleResolverTest {
    private static final Set<String> REGISTRY = loadRegistry();

    /**
     * Every language in Minecraft Java Edition 26.3 (142 asset-index lang
     * files plus en_us), as {@code clientCode=expected}; {@code -} means
     * "no signal, use the server default".
     */
    private static final List<String> MINECRAFT_LANGUAGES = List.of(
        "af_za=af_ZA", "ar_sa=ar_SA", "ast_es=ast_ES", "az_az=az_AZ", "ba_ru=ba_RU",
        "bar=de_DE", "be_by=be_BY", "be_latn=be_BY", "bg_bg=bg_BG", "br_fr=br_FR",
        "brb=nl_NL", "bs_ba=bs_BA", "ca_es=ca_ES", "cs_cz=cs_CZ", "cv_cu=cv_CU",
        "cy_gb=cy_GB", "da_dk=da_DK", "de_at=de_DE", "de_ch=de_DE", "de_de=de_DE",
        "el_gr=el_GR", "en_au=en_US", "en_ca=en_US", "en_gb=en_US", "en_nz=en_US",
        "en_pt=en_US", "en_ud=en_US", "en_us=-", "enp=en_US", "enws=en_US",
        "eo_uy=eo_UY", "es_ar=es_MX", "es_cl=es_MX", "es_ec=es_MX", "es_es=es_ES",
        "es_mx=es_MX", "es_uy=es_MX", "es_ve=es_MX", "esan=es_ES", "et_ee=et_EE",
        "eu_es=eu_ES", "fa_ir=fa_IR", "fi_fi=fi_FI", "fil_ph=fil_PH", "fo_fo=fo_FO",
        "fr_ca=fr_FR", "fr_ch=fr_FR", "fr_fr=fr_FR", "fra_de=de_DE", "fur_it=it_IT",
        "fy_nl=fy_NL", "ga_ie=ga_IE", "gd_gb=gd_GB", "gl_es=gl_ES", "go_fr=fr_FR",
        "got_de=-", "hal_ua=uk_UA", "haw_us=haw_US", "he_il=he_IL", "hi_in=hi_IN",
        "hn_no=nn_NO", "hr_hr=hr_HR", "hu_hu=hu_HU", "hy_am=hy_AM", "id_id=id_ID",
        "ig_ng=ig_NG", "io_en=-", "is_is=is_IS", "isv=-", "it_it=it_IT",
        "ja_jp=ja_JP", "jbo_en=-", "ka_ge=ka_GE", "kk_kz=kk_KZ", "kn_in=kn_IN",
        "ko_kr=ko_KR", "ksh=de_DE", "kw_gb=kw_GB", "ky_kg=ky_KG", "la_la=la_LA",
        "lb_lu=lb_LU", "li_li=li_LI", "lmo=it_IT", "lo_la=lo_LA", "lol_us=en_US",
        "lt_lt=lt_LT", "lv_lv=lv_LV", "lzh=zh_TW", "mk_mk=mk_MK", "mn_mn=mn_MN",
        "ms_my=ms_MY", "mt_mt=mt_MT", "nah=es_MX", "nds_de=nds_DE", "nl_be=nl_NL",
        "nl_nl=nl_NL", "nn_no=nn_NO", "no_no=nb_NO", "oc_fr=oc_FR", "ovd=sv_SE",
        "pl_pl=pl_PL", "pls=es_MX", "pt_br=pt_BR", "pt_pt=pt_PT", "qcb_es=es_ES",
        "qid=id_ID", "qya_aa=-", "ro_ro=ro_RO", "rpr=ru_RU", "ru_ru=ru_RU",
        "ry_ua=uk_UA", "sah_sah=ru_RU", "se_no=se_NO", "sk_sk=sk_SK", "sl_si=sl_SI",
        "so_so=so_SO", "sq_al=sq_AL", "sr_cs=sr_RS", "sr_sp=sr_RS", "sv_se=sv_SE",
        "sxu=de_DE", "szl=szl", "ta_in=ta_IN", "th_th=th_TH", "tl_ph=fil_PH",
        "tlh_aa=-", "tok=-", "tr_tr=tr_TR", "tt_ru=tt_RU", "tzo_mx=es_MX",
        "uk_ua=uk_UA", "uz_uz=uz_UZ", "val_es=ca_ES", "vec_it=vec_IT", "vi_vn=vi_VN",
        "vp_vl=-", "vro=et_EE", "yi_de=yi_DE", "yo_ng=yo_NG", "zh_cn=zh_CN",
        "zh_hk=zh_TW", "zh_tw=zh_TW", "zlm_arab=ms_MY"
    );

    @Test
    void everyMinecraftLanguageResolvesAsExpected() {
        assertEquals(143, MINECRAFT_LANGUAGES.size());
        for (String row : MINECRAFT_LANGUAGES) {
            String[] parts = row.split("=");
            String expected = parts[1].equals("-") ? null : parts[1];
            assertEquals(
                expected,
                ClientLocaleResolver.resolve(parts[0], REGISTRY),
                () -> "client language " + parts[0]
            );
        }
    }

    @Test
    void everyAliasTargetIsRegistered() {
        for (var alias : ClientLocaleResolver.ALIASES.entrySet()) {
            assertTrue(
                REGISTRY.contains(alias.getValue()),
                () -> alias.getKey() + " points at unregistered " + alias.getValue()
            );
        }
    }

    @Test
    void minecraftDefaultBlankAndMalformedCodesGiveNoSignal() {
        assertNull(ClientLocaleResolver.resolve("en_us", REGISTRY));
        assertNull(ClientLocaleResolver.resolve("EN-US", REGISTRY));
        assertNull(ClientLocaleResolver.resolve(null, REGISTRY));
        assertNull(ClientLocaleResolver.resolve("  ", REGISTRY));
        assertNull(ClientLocaleResolver.resolve("../en_gb", REGISTRY));
    }

    @Test
    void caseAndSeparatorAreIgnored() {
        assertEquals("pt_BR", ClientLocaleResolver.resolve("PT-BR", REGISTRY));
        assertEquals("nb_NO", ClientLocaleResolver.resolve(" No_No ", REGISTRY));
    }

    @Test
    void unknownRegionFallsBackToARegisteredCatalogOfTheSameLanguage() {
        assertEquals("fr_FR", ClientLocaleResolver.resolve("fr_be", REGISTRY));
        assertEquals("en_US", ClientLocaleResolver.resolve("en_ie", REGISTRY));
    }

    @Test
    void aliasWhoseTargetIsMissingFallsThroughToTheSameLanguage() {
        assertEquals(
            "es_ES",
            ClientLocaleResolver.resolve("es_ar", Set.of("en_US", "es_ES"))
        );
    }

    private static Set<String> loadRegistry() {
        try (InputStream stream = ClientLocaleResolverTest.class.getClassLoader()
            .getResourceAsStream(LocaleRegistry.RESOURCE)) {
            assertNotNull(stream, "Missing classpath resource " + LocaleRegistry.RESOURCE);
            return LocaleRegistry.load(stream).keySet();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
