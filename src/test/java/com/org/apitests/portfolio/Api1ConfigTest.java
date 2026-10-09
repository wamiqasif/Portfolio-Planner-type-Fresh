package com.org.apitests.portfolio;

import org.testng.annotations.Test;
import org.testng.Assert;
import com.org.apiautomation.auth.Api1AuthService;
import com.org.apiautomation.config.ConfigManager;
import org.testng.Assert;
import org.testng.annotations.Test;

/** API-free unit tests for the API-1 config keys and token extraction - safe to run with no credentials or network. */
public class Api1ConfigTest {

    @Test(groups = {"api1-unit"})
    public void api1Urls_matchTheConfirmedFlow() {
        ConfigManager config = ConfigManager.getInstance();
        Assert.assertEquals(config.getApi1BaseUrl(), "https://qaadvappapi.valueresearch.in");
        Assert.assertEquals(config.getApi1AuthBaseUrl(), "https://qaappapi.valueresearch.in");
        Assert.assertEquals(config.getApi1Endpoint(), "/api/v1/planner/funds-buy/generate");
        Assert.assertEquals(config.getApi1TokenUrl(), "/api/v0/auth/token/create/");
        Assert.assertEquals(config.getApi1LoginUrl(), "/api/v0/login/");
    }

    @Test(groups = {"api1-unit"})
    public void api1LoginHeaders_matchTheConfirmedShape() {
        ConfigManager config = ConfigManager.getInstance();
        Assert.assertEquals(config.getApi1AppPlatform(), "android");
        Assert.assertEquals(config.getApi1AppType(), "ADV_APP");
        Assert.assertEquals(config.getApi1AppVersion(), "1.0.33");
        Assert.assertEquals(config.getApi1SiteCode(), "VROADV");
        Assert.assertEquals(config.getApi1Device(), "android");
        Assert.assertEquals(config.getApi1Platform(), "VROAPP");
    }

    @Test(groups = {"api1-unit"})
    public void extractField_readsOnlyTheRequestedTopLevelField() {
        String body = "{\"token\":\"abc\",\"aa\":\"x\",\"redirect_url\":\"http://h/?token=zzz\",\"status\":true}";
        Assert.assertEquals(Api1AuthService.extractField(body, "token"), "abc");
        Assert.assertEquals(Api1AuthService.extractField(body, "status"), "true");
    }

    @Test(groups = {"api1-unit"})
    public void extractField_toleratesLeadingBom() {
        Assert.assertEquals(Api1AuthService.extractField("﻿{\"access_token\":\"jwt\"}", "access_token"), "jwt");
    }

    @Test(groups = {"api1-unit"})
    public void extractField_returnsNullForMissingBlankOrInvalid() {
        Assert.assertNull(Api1AuthService.extractField("{\"token\":\"\"}", "token"));
        Assert.assertNull(Api1AuthService.extractField("{\"a\":1}", "token"));
        Assert.assertNull(Api1AuthService.extractField("not json", "token"));
        Assert.assertNull(Api1AuthService.extractField(null, "token"));
    }
}
