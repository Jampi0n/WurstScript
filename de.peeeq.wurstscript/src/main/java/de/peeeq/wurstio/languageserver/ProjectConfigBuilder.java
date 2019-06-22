package de.peeeq.wurstio.languageserver;

import com.google.common.io.Files;
import config.*;
import de.peeeq.wurstio.Pjass;
import de.peeeq.wurstio.languageserver.requests.RequestFailedException;
import de.peeeq.wurstio.mpq.MpqEditor;
import de.peeeq.wurstio.mpq.MpqEditorFactory;
import de.peeeq.wurstscript.RunArgs;
import net.moonlightflower.wc3libs.bin.app.MapHeader;
import net.moonlightflower.wc3libs.bin.app.W3I;
import net.moonlightflower.wc3libs.dataTypes.app.Controller;
import org.apache.commons.lang.StringUtils;
import org.eclipse.lsp4j.MessageType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProjectConfigBuilder {
    public static final String FILE_NAME = "wurst.build";

    public static void apply(WurstProjectConfigData projectConfig, File targetMap, File compiledScript, File buildDir, RunArgs runArgs) throws IOException {
        if (projectConfig.getProjectName().isEmpty()) {
            throw new RequestFailedException(MessageType.Error, "wurst.build is missing projectName.");
        }


        try (MpqEditor mpq = MpqEditorFactory.getEditor((targetMap))) {
            File file = new File(buildDir, "wc3libs.j");
            byte[] scriptBytes;
            if (!projectConfig.getBuildMapData().getName().isEmpty()) {
                // Apply w3i config values
                W3I w3I = prepareW3I(projectConfig, targetMap);
                FileInputStream inputStream = new FileInputStream(compiledScript);
                StringWriter sw = new StringWriter();
                w3I.injectConfigsInJassScript(inputStream, sw);

                scriptBytes = sw.toString().getBytes(StandardCharsets.UTF_8);

                File w3iFile = new File("w3iFile");
                if (runArgs.isLua()) {
                    w3I.setScriptLang(W3I.SCRIPT_LANG_LUA);
                    w3I.setFileVersion(W3I.EncodingFormat.W3I_0x1C.getVersion());
                }
                w3I.write(w3iFile);

                mpq.deleteFile("war3map.w3i");
                mpq.insertFile("war3map.w3i", java.nio.file.Files.readAllBytes(w3iFile.toPath()));

                w3iFile.delete();
            } else {
                scriptBytes = java.nio.file.Files.readAllBytes(compiledScript.toPath());
            }

            Files.write(scriptBytes, file);
            Pjass.runPjass(file);
            mpq.deleteFile("war3map.j");
            mpq.insertFile("war3map.j", scriptBytes);

            file.delete();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        applyMapHeader(projectConfig, targetMap);
    }

    private static W3I prepareW3I(WurstProjectConfigData projectConfig, File targetMap) throws Exception {
        try (MpqEditor mpq = MpqEditorFactory.getEditor((targetMap))) {
            W3I w3I = new W3I(mpq.extractFile("war3map.w3i"));
            WurstProjectBuildMapData buildMapData = projectConfig.getBuildMapData();
            if (StringUtils.isNotBlank(buildMapData.getName())) {
                w3I.setMapName(buildMapData.getName());
            }
            if (StringUtils.isNotBlank(buildMapData.getAuthor())) {
                w3I.setMapAuthor(buildMapData.getAuthor());
            }
            applyScenarioData(w3I, buildMapData);

            if (buildMapData.getPlayers().size() > 0) {
                applyPlayers(projectConfig, w3I);
            }
            if (buildMapData.getForces().size() > 0) {
                applyForces(projectConfig, w3I);
            }
            return w3I;
        }
    }

    private static void applyScenarioData(W3I w3I, WurstProjectBuildMapData buildMapData) {
        WurstProjectBuildScenarioData scenarioData = buildMapData.getScenarioData();
        if (StringUtils.isNotBlank(scenarioData.getSuggestedPlayers())) {
            w3I.setPlayersRecommendedAmount(scenarioData.getSuggestedPlayers());
        }
        if (StringUtils.isNotBlank(scenarioData.getDescription())) {
            w3I.setMapDescription(scenarioData.getDescription());
        }
        if (scenarioData.getLoadingScreen() != null) {
            applyLoadingScreen(w3I, scenarioData.getLoadingScreen());
        }
    }

    private static void applyLoadingScreen(W3I w3I, WurstProjectBuildLoadingScreenData loadingScreen) {
        w3I.setLoadingScreenModel(loadingScreen.getModel());
        w3I.getLoadingScreen().setTitle(loadingScreen.getTitle());
        w3I.getLoadingScreen().setSubtitle(loadingScreen.getSubTitle());
        w3I.getLoadingScreen().setText(loadingScreen.getText());
    }

    private static void applyForces(WurstProjectConfigData projectConfig, W3I w3I) {
        w3I.clearForces();
        ArrayList<WurstProjectBuildForce> forces = projectConfig.getBuildMapData().getForces();
        for (WurstProjectBuildForce wforce : forces) {
            W3I.Force force = w3I.addForce();
            System.err.println("Setting name: " + wforce.getName());
            force.setName(wforce.getName());
            force.setFlag(W3I.Force.Flags.Flag.ALLIED, wforce.getFlags().getAllied());
            force.setFlag(W3I.Force.Flags.Flag.ALLIED_VICTORY, wforce.getFlags().getAlliedVictory());
            force.setFlag(W3I.Force.Flags.Flag.SHARED_VISION, wforce.getFlags().getSharedVision());
            force.setFlag(W3I.Force.Flags.Flag.SHARED_UNIT_CONTROL, wforce.getFlags().getSharedControl());
            force.setFlag(W3I.Force.Flags.Flag.SHARED_UNIT_CONTROL_ADVANCED, wforce.getFlags().getSharedControlAdvanced());
            force.addPlayerNums(wforce.getPlayerIds());
        }
    }

    private static void applyPlayers(WurstProjectConfigData projectConfig, W3I w3I) {
        List<W3I.Player> existing = new ArrayList<>(w3I.getPlayers());
        w3I.getPlayers().clear();
        ArrayList<WurstProjectBuildPlayer> players = projectConfig.getBuildMapData().getPlayers();
        for (WurstProjectBuildPlayer wplayer : players) {
            Optional<W3I.Player> old = existing.stream().filter(player -> player.getNum() == wplayer.getId()).findFirst();
            W3I.Player player = w3I.addPlayer();
            player.setNum(wplayer.getId());

            old.ifPresent(player1 -> applyExistingPlayerConfig(player1, player));

            setVolatilePlayerConfig(wplayer, player);
        }
    }

    private static void applyExistingPlayerConfig(W3I.Player oldPlayer, W3I.Player player) {
        player.setStartPos(oldPlayer.getStartPos());
        player.setName(oldPlayer.getName());
        player.setRace(oldPlayer.getRace());
        player.setType(oldPlayer.getType());
        player.setStartPosFixed(oldPlayer.getStartPosFixed());
        player.setAllyLowPrioFlags(oldPlayer.getAllyLowPrioFlags());
        player.setAllyHighPrioFlags(oldPlayer.getAllyHighPrioFlags());
    }

    private static void setVolatilePlayerConfig(WurstProjectBuildPlayer wplayer, W3I.Player player) {
        if (wplayer.getName() != null) {
            player.setName(wplayer.getName());
        }

        if (wplayer.getRace() != null) {
            W3I.Player.UnitRace val = W3I.Player.UnitRace.valueOf(wplayer.getRace().toString());
            if (val != null) {
                player.setRace(val);
            }
        }
        if (wplayer.getController() != null) {
            net.moonlightflower.wc3libs.dataTypes.app.Controller val1 = Controller.valueOf(wplayer.getController().toString());
            if (val1 != null) {
                player.setType(val1);
            }
        }
        if (wplayer.getFixedStartLoc() != null) {
            player.setStartPosFixed(wplayer.getFixedStartLoc() ? 1 : 0);
        }
    }

    private static void applyMapHeader(WurstProjectConfigData projectConfig, File targetMap) throws IOException {
        MapHeader mapHeader = MapHeader.ofFile(targetMap);
        if (projectConfig.getBuildMapData().getPlayers().size() > 0) {
            mapHeader.setMaxPlayersCount(projectConfig.getBuildMapData().getPlayers().size());
        }
        if (StringUtils.isNotBlank(projectConfig.getBuildMapData().getName())) {
            mapHeader.setMapName(projectConfig.getBuildMapData().getName());
        }
        mapHeader.writeToMapFile(targetMap);
    }
}
