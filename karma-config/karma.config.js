// https://karma-runner.github.io/4.0/config/configuration-file.html
// This is not the normal Karma config file but will be pasted into one
// https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/js/testing/karma/KotlinKarma.kt#L483

config.set({
    browserNoActivityTimeout: 60000,
    browserDisconnectTimeout: 60000
});

// Used to print what the config is
//throw new Error(JSON.stringify(config));
/*config.set({
    customLaunchers: {
        ChromiumHeadless: {
        // https://peter.sh/experiments/chromium-command-line-switches/ but it does not work
            flags: ["--data-quota-bytes=50000"]
        }
    }
});*/
