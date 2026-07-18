package dev.genesi.connectfour.board;

import dev.genesi.connectfour.model.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure Connect Four rules on a columns×rows grid. {@code null} = empty.
 */
public final class BoardLogic {

    private BoardLogic() {
    }

    public static int lowestEmptyRow(Team[][] grid, int column) {
        if (column < 0 || column >= grid.length) {
            return -1;
        }
        int rows = grid[column].length;
        for (int row = 0; row < rows; row++) {
            if (grid[column][row] == null) {
                return row;
            }
        }
        return -1;
    }

    public static boolean isBoardFull(Team[][] grid) {
        for (Team[] column : grid) {
            for (Team cell : column) {
                if (cell == null) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isColumnFull(Team[][] grid, int column) {
        return lowestEmptyRow(grid, column) < 0;
    }

    /**
     * @return winning cells as [column, row] pairs, or empty if no win
     */
    public static List<int[]> findWinningLine(Team[][] grid, Team team) {
        int columns = grid.length;
        int rows = grid[0].length;
        int[][] directions = {
                {1, 0},  // horizontal
                {0, 1},  // vertical
                {1, 1},  // diagonal /
                {1, -1}  // diagonal \
        };
        for (int c = 0; c < columns; c++) {
            for (int r = 0; r < rows; r++) {
                if (grid[c][r] != team) {
                    continue;
                }
                for (int[] dir : directions) {
                    List<int[]> line = new ArrayList<>(4);
                    boolean win = true;
                    for (int i = 0; i < 4; i++) {
                        int nc = c + dir[0] * i;
                        int nr = r + dir[1] * i;
                        if (nc < 0 || nc >= columns || nr < 0 || nr >= rows || grid[nc][nr] != team) {
                            win = false;
                            break;
                        }
                        line.add(new int[]{nc, nr});
                    }
                    if (win) {
                        return line;
                    }
                }
            }
        }
        return List.of();
    }

    public static boolean hasWin(Team[][] grid, Team team) {
        return !findWinningLine(grid, team).isEmpty();
    }
}
