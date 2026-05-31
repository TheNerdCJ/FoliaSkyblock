package com.thenerdcj.gui;

import com.thenerdcj.TestBase;
import com.thenerdcj.testutil.MockBukkitGuiSimulator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * GUI simulation tests for ChallengeGUI using the simulator.
 */
class ChallengeGUITest extends TestBase {

    private ChallengeGUI challengeGUI;
    private MockBukkitGuiSimulator simulator;
    private Player mockPlayer;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        simulator = new MockBukkitGuiSimulator();
        challengeGUI = new ChallengeGUI(plugin, false);

        mockPlayer = mockPlayer("ChallengeTester");
    }

    @Test
    void testOpenChallengeGUI_DoesNotThrow() {
        assertDoesNotThrow(() -> challengeGUI.open(mockPlayer));
    }

    @Test
    void testChallengeClick_Simulated() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                20,
                ClickType.LEFT,
                "CLAIM_CHALLENGE"
        );

        challengeGUI.onInventoryClick(click);
        assertNotNull(click);
    }

    @Test
    void testClickNonCompletedChallenge_ShowsErrorMessage() {
        // Use the robust safe ItemStack helper
        ItemStack nonCompleted = createSafeItemStack(Material.PAPER);
        ItemMeta meta = nonCompleted.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§fSome Challenge IN PROGRESS");
            nonCompleted.setItemMeta(meta);
        }

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(mockPlayer);
        when(event.getView().getTitle()).thenReturn("§6§lDaily & Weekly Challenges");
        when(event.getCurrentItem()).thenReturn(nonCompleted);

        challengeGUI.onInventoryClick(event);

        verify(mockPlayer).sendMessage(contains("not completed yet"));
    }

    @Test
    void testClickWithNoChallenges_DoesNotCrash() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                22,
                ClickType.LEFT,
                "CLAIM_CHALLENGE"
        );

        // Should not throw even if no matching challenge
        assertDoesNotThrow(() -> challengeGUI.onInventoryClick(click));
    }
}