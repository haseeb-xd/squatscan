package com.haseeb.squatscan.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HookRunnerTest {

    @Test
    void extractsPackagesFromInstallVariants() {
        assertThat(HookRunner.extractPackages("npm install express lodahs --save-dev"))
                .containsExactly("express", "lodahs");
        assertThat(HookRunner.extractPackages("npm i -D @types/node zod@3.22.0"))
                .containsExactly("@types/node", "zod");
        assertThat(HookRunner.extractPackages("pnpm add chalkk")).containsExactly("chalkk");
        assertThat(HookRunner.extractPackages("yarn add axois")).containsExactly("axois");
        assertThat(HookRunner.extractPackages("bun add raect")).containsExactly("raect");
    }

    @Test
    void handlesCompoundCommandsWithoutCheckingOtherWords() {
        assertThat(HookRunner.extractPackages("cd app && npm install expres && npm run build"))
                .containsExactly("expres");
        assertThat(HookRunner.extractPackages("npm i lodahs 2>&1 | tail -5"))
                .containsExactly("lodahs");
    }

    @Test
    void checksOnlyThePackageForRunners() {
        assertThat(HookRunner.extractPackages("npx create-react-app my-app"))
                .containsExactly("create-react-app");
        assertThat(HookRunner.extractPackages("pnpm dlx some-tool --flag value"))
                .containsExactly("some-tool");
    }

    @Test
    void ignoresNonInstallCommandsAndLocalSources() {
        assertThat(HookRunner.extractPackages("npm install")).isEmpty();
        assertThat(HookRunner.extractPackages("npm run test")).isEmpty();
        assertThat(HookRunner.extractPackages("git status")).isEmpty();
        assertThat(HookRunner.extractPackages("npm install ./local-pkg https://example.com/x.tgz"))
                .isEqualTo(List.of());
    }
}
