package com.intechcore.polarion.extension.github;

import ch.sbb.polarion.extension.generic.GenericUiServlet;

import java.io.Serial;

public class GithubAppServlet extends GenericUiServlet {

    @Serial
    private static final long serialVersionUID = 6913284471029385716L;

    public GithubAppServlet() {
        super("github-app");
    }

}
