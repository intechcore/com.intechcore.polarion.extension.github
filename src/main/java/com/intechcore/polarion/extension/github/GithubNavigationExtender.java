package com.intechcore.polarion.extension.github;

import com.polarion.alm.ui.server.navigation.NavigationExtender;
import com.polarion.alm.ui.server.navigation.NavigationExtenderNode;
import com.polarion.subterra.base.data.identification.IContextId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The topic GitHub in the navigation of a project. It opens the page of the open GitHub items, where
 * a user creates work items in their own name.
 */
public class GithubNavigationExtender extends NavigationExtender {

    public static final String ID = "github";

    @NotNull
    @Override
    public String getId() {
        return ID;
    }

    @NotNull
    @Override
    public String getLabel() {
        return "GitHub";
    }

    @Nullable
    @Override
    public String getIconUrl() {
        return "/polarion/github-app/ui/images/menu/30x30/_parent.svg";
    }

    @Nullable
    @Override
    public String getPageUrl(@NotNull IContextId contextId) {
        String contextName = contextId.getContextName();
        String scope = contextName == null ? "" : "project/%s/".formatted(contextName);
        return "/polarion/github-app/ui/app/index.html?feature=items&embedded=true&scope=" + scope;
    }

    @Override
    public boolean requiresToken() {
        return false;
    }

    @NotNull
    @Override
    public List<NavigationExtenderNode> getRootNodes(@NotNull IContextId contextId) {
        return new ArrayList<>();
    }
}
