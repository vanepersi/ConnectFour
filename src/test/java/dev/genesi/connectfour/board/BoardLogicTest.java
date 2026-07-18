package dev.genesi.connectfour.board;

import dev.genesi.connectfour.model.Team;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardLogicTest {

    @Test
    void dropsToLowestRow() {
        Team[][] grid = empty(7, 6);
        assertEquals(0, BoardLogic.lowestEmptyRow(grid, 0));
        grid[0][0] = Team.RED;
        assertEquals(1, BoardLogic.lowestEmptyRow(grid, 0));
        for (int r = 0; r < 6; r++) {
            grid[0][r] = Team.YELLOW;
        }
        assertEquals(-1, BoardLogic.lowestEmptyRow(grid, 0));
        assertTrue(BoardLogic.isColumnFull(grid, 0));
    }

    @Test
    void detectsHorizontalWin() {
        Team[][] grid = empty(7, 6);
        for (int c = 1; c <= 4; c++) {
            grid[c][2] = Team.RED;
        }
        List<int[]> line = BoardLogic.findWinningLine(grid, Team.RED);
        assertEquals(4, line.size());
        assertTrue(BoardLogic.hasWin(grid, Team.RED));
        assertFalse(BoardLogic.hasWin(grid, Team.YELLOW));
    }

    @Test
    void detectsVerticalWin() {
        Team[][] grid = empty(7, 6);
        for (int r = 0; r < 4; r++) {
            grid[3][r] = Team.YELLOW;
        }
        assertTrue(BoardLogic.hasWin(grid, Team.YELLOW));
    }

    @Test
    void detectsDiagonalWin() {
        Team[][] grid = empty(7, 6);
        grid[0][0] = Team.RED;
        grid[1][1] = Team.RED;
        grid[2][2] = Team.RED;
        grid[3][3] = Team.RED;
        assertTrue(BoardLogic.hasWin(grid, Team.RED));
    }

    @Test
    void boardFullWhenNoEmpty() {
        Team[][] grid = empty(7, 6);
        assertFalse(BoardLogic.isBoardFull(grid));
        for (int c = 0; c < 7; c++) {
            for (int r = 0; r < 6; r++) {
                grid[c][r] = (c + r) % 2 == 0 ? Team.RED : Team.YELLOW;
            }
        }
        assertTrue(BoardLogic.isBoardFull(grid));
    }

    private static Team[][] empty(int columns, int rows) {
        return new Team[columns][rows];
    }
}
