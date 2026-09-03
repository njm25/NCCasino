package org.nc.nccasino.games.Slots;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the redesign audit's Section 4 fix and its post-audit correction
 * (Section 5): the specific vanilla materials that glint with no
 * enchantment present, and -- critically -- inspects
 * {@link SlotsControlPresentation}'s actual production role catalog rather
 * than hardcoded material literals. {@code SlotsMachine} consumes that same
 * catalog for every fixed-material control it renders, so if a future change
 * reverted a control back to {@code ENCHANTED_BOOK}/{@code KNOWLEDGE_BOOK},
 * this test would be inspecting that real, current choice and would catch
 * it -- not a literal the test happened to still agree with.
 */
class SlotsGlintPolicyTest {

    @Test
    void flagsKnownInherentlyGlintingMaterials() {
        assertTrue(SlotsGlintPolicy.hasInherentGlint(Material.ENCHANTED_BOOK));
        assertTrue(SlotsGlintPolicy.hasInherentGlint(Material.ENCHANTED_GOLDEN_APPLE));
        assertTrue(SlotsGlintPolicy.hasInherentGlint(Material.KNOWLEDGE_BOOK));
    }

    @Test
    void doesNotFlagOrdinaryMaterials() {
        assertFalse(SlotsGlintPolicy.hasInherentGlint(Material.STONE));
        assertFalse(SlotsGlintPolicy.hasInherentGlint(null));
    }

    @Test
    void noProductionControlNotApprovedToGlintUsesAnInherentlyGlintingMaterial() {
        // Reads SlotsMachine's actual material choice for every named
        // control -- SlotsControlPresentation.Role is the single source of
        // truth SlotsMachine itself renders from -- and fails if any
        // non-approved role's material inherently glints.
        for (SlotsControlPresentation.Role role : SlotsControlPresentation.Role.values()) {
            if (role.approvedToGlint()) {
                continue;
            }
            assertFalse(SlotsGlintPolicy.hasInherentGlint(role.material()),
                role + " (" + role.material() + ") is not an approved-glint role but its material inherently glints");
        }
    }

    @Test
    void exactlyOneFixedMaterialRoleIsApprovedToGlintAndItIsReadySpin() {
        // The other two approved-glint roles (matched paid winning symbol,
        // matched hypothetical Demo winning symbol) intentionally have no
        // entry in SlotsControlPresentation.Role at all -- their material is
        // whichever SlotsSymbol matched, not a fixed control material (see
        // SlotsMachine.highlightLine). So among the *fixed-material* roles
        // catalogued here, exactly one may glint: a ready real Spin.
        long approvedCount = 0;
        for (SlotsControlPresentation.Role role : SlotsControlPresentation.Role.values()) {
            if (role.approvedToGlint()) {
                approvedCount++;
                assertTrue(role == SlotsControlPresentation.Role.SPIN_READY
                        || role == SlotsControlPresentation.Role.AUTO_SPIN_CONTROL,
                    "unexpected approved-glint fixed-material role: " + role);
            }
        }
        assertTrue(approvedCount == 2, "expected exactly two approved-glint fixed-material roles");
    }

    @Test
    void spinActivePaytableAndBackAreNeverApprovedToGlint() {
        assertFalse(SlotsControlPresentation.Role.SPIN_ACTIVE.approvedToGlint());
        assertFalse(SlotsControlPresentation.Role.PAYTABLE_OPEN.approvedToGlint());
        assertFalse(SlotsControlPresentation.Role.BACK_TO_GAME.approvedToGlint());
        assertFalse(SlotsControlPresentation.Role.PAYOUT_BLOCKED.approvedToGlint());
        assertFalse(SlotsControlPresentation.Role.NEUTRAL_CELL.approvedToGlint());
    }

    @Test
    void theClockIsApprovedToGlintOnlyBecauseARunningBatchExplicitlyGlintsIt() {
        // The Clock is the one control that signals a live state by glinting:
        // Auto Spin running. Its material must still be inherently glint-free,
        // so an idle Clock does not glint on its own.
        assertTrue(SlotsControlPresentation.Role.AUTO_SPIN_CONTROL.approvedToGlint());
        assertFalse(SlotsGlintPolicy.hasInherentGlint(
            SlotsControlPresentation.Role.AUTO_SPIN_CONTROL.material()));
    }
}
