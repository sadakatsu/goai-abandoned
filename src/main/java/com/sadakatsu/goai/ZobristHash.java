package com.sadakatsu.goai;

import static com.sadakatsu.go.domain.intersection.Empty.EMPTY;
import static com.sadakatsu.go.domain.intersection.PermanentlyUnplayable.PERMANENTLY_UNPLAYABLE;
import static com.sadakatsu.go.domain.intersection.Stone.BLACK;
import static com.sadakatsu.go.domain.intersection.Stone.WHITE;
import static com.sadakatsu.go.domain.intersection.TemporarilyUnplayable.TEMPORARILY_UNPLAYABLE;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.sadakatsu.go.domain.Coordinate;
import com.sadakatsu.go.domain.Game;
import com.sadakatsu.go.domain.intersection.Intersection;
import com.sadakatsu.go.domain.intersection.Player;

public class ZobristHash {
    private static final BigInteger SHIFT;
    private static final Map<Intersection, BigInteger> HASHES;
    
    static {
        SHIFT = BigInteger.valueOf(5L);
        
        HASHES = new HashMap<>();
        HASHES.put(EMPTY, BigInteger.ZERO);
        HASHES.put(BLACK, BigInteger.ONE);
        HASHES.put(WHITE, BigInteger.valueOf(2L));
        HASHES.put(TEMPORARILY_UNPLAYABLE, BigInteger.valueOf(3L));
        HASHES.put(PERMANENTLY_UNPLAYABLE, BigInteger.valueOf(4L));
    }
    
    private final BigInteger canonicalHash;
    private final BigInteger originalHash;
    private final int canonicalIndex;
    private final int dimension;
    private final Intersection canonicalPlayer;
    private final Intersection originalPlayer;
    
    private Integer javaHash;
    
    public ZobristHash( Game game ) {
        dimension = game.getDimension();
        
        Intersection player;
        try {
            player = (Intersection) game.getCurrentPlayer();
        } catch (IllegalStateException e) {
            player = EMPTY; // Yes, EMPTY is not a Player, but this allows hashing endgame positions.
        }
        originalPlayer = player;
        
        Intersection playerAfterPass = (game.wouldPassEndGame() ? EMPTY : invertIntersection(originalPlayer));
        Intersection invertedPlayerAfterPass = invertIntersection(playerAfterPass);
        
        int intersections = dimension * dimension;
        Intersection[][] variants = new Intersection[16][intersections + 2];
        for (int row = 0, rowOpposite = dimension - 1; row < dimension; ++row, --rowOpposite) {
            for (int column = 0, columnOpposite = dimension - 1; column < dimension; ++column, --columnOpposite) {
                Coordinate coordinate = Coordinate.get(column + 1, row + 1);
                Intersection value = game.get(coordinate);
                Intersection inverted = invertIntersection(value);
                int[] boardIndices = new int[] {
                    getBoardIndex(row, column),                 // no flips, no rotations
                    getBoardIndex(row, columnOpposite),         // flip horizontally, no rotations
                    getBoardIndex(rowOpposite, column),         // flip vertically, no rotations
                    getBoardIndex(rowOpposite, columnOpposite), // flip both OR rotate twice
                    getBoardIndex(column, row),                 // flip horizontally, rotate 90 (left)
                    getBoardIndex(column, rowOpposite),         // no flips, rotate 90 (left)
                    getBoardIndex(columnOpposite, row),         // no flips, rotate -90 (right)
                    getBoardIndex(columnOpposite, rowOpposite)  // flip horizontally, rotate -90 (right)
                };
                for (int variantIndex = 0; variantIndex < 8; ++variantIndex) {
                    int boardIndex = boardIndices[variantIndex];
                    variants[variantIndex][boardIndex] = value;
                    variants[variantIndex + 8][boardIndex] = inverted;
                }
            }
        }
        
        Intersection invertedPlayer = invertIntersection(player);
        for (int i = 0; i < 8; ++i) {
            variants[i][intersections] = playerAfterPass;
            variants[i][intersections + 1] = player;
            variants[i + 8][intersections] = invertedPlayerAfterPass;
            variants[i + 8][intersections + 1] = invertedPlayer;
        }
        
        originalHash = buildHash(variants[0]);
        BigInteger chosenHash = originalHash;
        int chosenIndex = 0;
        Intersection chosenPlayer = player;
        for (int currentIndex = 1; currentIndex < 16; ++currentIndex) {
            BigInteger currentHash = buildHash(variants[currentIndex]);
            if (currentHash.compareTo(chosenHash) < 0) {
                chosenHash = currentHash;
                chosenIndex = currentIndex;
                if (currentIndex >= 8) {
                    chosenPlayer = invertedPlayer;
                }
            }
        }
        
        canonicalPlayer = chosenPlayer;
        canonicalHash = chosenHash;
        canonicalIndex = chosenIndex;
    }
    
    private Intersection invertIntersection( Intersection value ) {
        Intersection inverted = value;
        if (value instanceof Player) {
            inverted = (Intersection) (((Player) value).getOpposite());
        }
        return inverted;
    }
    
    private int getBoardIndex( int row, int column ) {
        return row * dimension + column;
    }
    
    private BigInteger buildHash( Intersection[] values ) {
        BigInteger hash = BigInteger.ZERO;
        
        for (int i = values.length - 1; i >= 0; --i) {
            Intersection value = values[i];
            BigInteger addend = HASHES.get(value);
            hash = hash.multiply(SHIFT).add(addend);
        }
        
        return hash;
    }
    
    public BigInteger getCanonicalHash() {
        return canonicalHash;
    }

    public BigInteger getOriginalHash() {
        return originalHash;
    }

    public int getCanonicalIndex() {
        return canonicalIndex;
    }

    public int getDimension() {
        return dimension;
    }

    public Intersection getCanonicalPlayer() {
        return canonicalPlayer;
    }

    public Intersection getOriginalPlayer() {
        return originalPlayer;
    }
    
    public boolean exactlyEquals( ZobristHash that ) {
        return (
            this == that ||
            that != null && this.dimension == that.dimension && this.originalHash.equals(that.originalHash)
        );
    }

    @Override
    public boolean equals( Object other ) {
        boolean result = this == other;
        if (!result && other != null && ZobristHash.class.equals(other.getClass())) {
            ZobristHash that = (ZobristHash) other;
            result = this.dimension == that.dimension && this.canonicalHash.equals(that.canonicalHash);
        }
        return result;
    }
    
    @Override
    public int hashCode() {
        if (javaHash == null) {
            HashCodeBuilder builder = new HashCodeBuilder();
            builder.append(dimension);
            builder.append(canonicalHash);
            javaHash = builder.toHashCode();
        }
        
        return javaHash;
    }
    
    @Override
    public String toString() {
        return String.format(
            "ZobristHash{ dimension=%d, canonicalPlayer=%s, canonicalHash=%s, originalPlayer=%s, originalHash=%s, canonicalIndex=%d }",
            dimension,
            canonicalPlayer,
            canonicalHash,
            originalPlayer,
            originalHash,
            canonicalIndex
        );
    }
}
