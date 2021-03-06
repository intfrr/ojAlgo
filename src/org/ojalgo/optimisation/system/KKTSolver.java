/*
 * Copyright 1997-2015 Optimatika (www.optimatika.se)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.ojalgo.optimisation.system;

import static org.ojalgo.constant.PrimitiveMath.*;

import org.ojalgo.array.Array1D;
import org.ojalgo.matrix.decomposition.Cholesky;
import org.ojalgo.matrix.decomposition.Eigenvalue;
import org.ojalgo.matrix.decomposition.LU;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.PrimitiveDenseStore;
import org.ojalgo.matrix.store.ZeroStore;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Optimisation.Options;

/**
 * When the KKT matrix is nonsingular, there is a unique optimal primal-dual pair (x,l). If the KKT matrix is
 * singular, but the KKT system is still solvable, any solution yields an optimal pair (x,l). If the KKT
 * system is not solvable, the quadratic optimization problem is unbounded below or infeasible.
 *
 * @author apete
 */
public final class KKTSolver extends Object {

    public static final class Input {

        private final MatrixStore<Double> myA;
        private final MatrixStore<Double> myB;
        private final MatrixStore<Double> myC;
        private final MatrixStore<Double> myQ;

        /**
         * | Q | = | C |
         *
         * @param Q
         * @param C
         */
        public Input(final MatrixStore<Double> Q, final MatrixStore<Double> C) {
            this(Q, C, null, null);
        }

        /**
         * | Q | A<sup>T</sup> | = | C | <br>
         * | A | 0 | = | B |
         *
         * @param Q
         * @param C
         * @param A
         * @param B
         */
        public Input(final MatrixStore<Double> Q, final MatrixStore<Double> C, final MatrixStore<Double> A, final MatrixStore<Double> B) {

            super();

            myQ = Q;
            myC = C;
            myA = A;
            myB = B;
        }

        final MatrixStore<Double> getA() {
            return myA;
        }

        final MatrixStore<Double> getB() {
            return myB;
        }

        final MatrixStore<Double> getC() {
            return myC;
        }

        final MatrixStore<Double> getKKT() {
            if (myA != null) {
                return myQ.builder().right(myA.transpose()).below(myA).build();
            } else {
                return myQ;
            }
        }

        final MatrixStore<Double> getQ() {
            return myQ;
        }

        final MatrixStore<Double> getRHS() {
            if (myB != null) {
                return myC.builder().below(myB).build();
            } else {
                return myC;
            }
        }

        final boolean isConstrained() {
            return (myA != null) && (myA.count() > 0L);
        }

    }

    public static final class Output {

        private final MatrixStore<Double> myL;
        private final boolean mySolvable;
        private final MatrixStore<Double> myX;

        Output(final MatrixStore<Double> X, final MatrixStore<Double> L, final boolean solvable) {

            super();

            myX = X;
            myL = L;
            mySolvable = solvable;
        }

        public final MatrixStore<Double> getL() {
            return myL;
        }

        public final MatrixStore<Double> getX() {
            return myX;
        }

        public final boolean isSolvable() {
            return mySolvable;
        }

        /**
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {

            final StringBuilder tmpSB = new StringBuilder();

            tmpSB.append(mySolvable);

            if (mySolvable) {

                tmpSB.append(" X=");

                tmpSB.append(Array1D.PRIMITIVE.copy(this.getX()));

                tmpSB.append(" L=");

                tmpSB.append(Array1D.PRIMITIVE.copy(this.getL()));
            }

            return tmpSB.toString();
        }

    }

    private static final Options DEFAULT_OPTIONS = new Optimisation.Options();

    private final Cholesky<Double> myCholesky;
    private final LU<Double> myLU;

    public KKTSolver() {

        super();

        myCholesky = Cholesky.makePrimitive();
        myLU = LU.makePrimitive();
    }

    public KKTSolver(final KKTSolver.Input template) {

        super();

        final MatrixStore<Double> tmpQ = template.getQ();

        myCholesky = Cholesky.make(tmpQ);
        myLU = LU.make(tmpQ);
    }

    public Output solve(final Input input) {
        return this.solve(input, DEFAULT_OPTIONS);
    }

    private transient PrimitiveDenseStore myX = null;

    PrimitiveDenseStore getX(final Input input) {
        if (myX == null) {
            myX = PrimitiveDenseStore.FACTORY.makeZero(input.getQ().countRows(), 1L);
        }
        return myX;
    }

    public Output solve(final Input input, final Optimisation.Options options) {

        final MatrixStore<Double> tmpQ = input.getQ();
        final MatrixStore<Double> tmpC = input.getC();
        final MatrixStore<Double> tmpA = input.getA();
        final MatrixStore<Double> tmpB = input.getB();

        boolean tmpSolvable = true;
        if (options.validate) {
            this.doValidate(input);
        }

        final PrimitiveDenseStore tmpX = this.getX(input);
        MatrixStore<Double> tmpL = null;

        if (input.isConstrained() && (tmpA.countRows() == tmpA.countColumns()) && myLU.decompose(tmpA) && (tmpSolvable = myLU.isSolvable())) {
            // Only 1 possible solution

            myLU.solve(tmpB, tmpX);

            myLU.decompose(tmpA.transpose()); //TODO Shouldn't have to do this. Can solve directly with the already calculated  myLU.compute(tmpA).
            tmpL = myLU.solve(tmpC.subtract(tmpQ.multiply(tmpX)));

        } else if (myCholesky.decompose(tmpQ) && (tmpSolvable = myCholesky.isSolvable())) {
            // Q is SPD

            if (!input.isConstrained()) {
                // Unconstrained

                myCholesky.solve(tmpC, tmpX);
                tmpL = ZeroStore.makePrimitive(0, 1);

            } else {
                // Actual/normal optimisation problem

                final MatrixStore<Double> tmpInvQAT = myCholesky.solve(tmpA.transpose());

                // Negated Schur complement
                final MatrixStore<Double> tmpS = tmpInvQAT.multiplyLeft(tmpA);
                if (myLU.decompose(tmpS) && (tmpSolvable = myLU.isSolvable())) {

                    final MatrixStore<Double> tmpInvQC = myCholesky.solve(tmpC);

                    tmpL = myLU.solve(tmpInvQC.multiplyLeft(tmpA).subtract(tmpB));
                    myCholesky.solve(tmpC.subtract(tmpL.multiplyLeft(tmpA.transpose())), tmpX);
                }
            }
        }

        if (!tmpSolvable && myLU.decompose(input.getKKT()) && (tmpSolvable = myLU.isSolvable())) {
            // The above failed
            // Try solving the full KKT system instaed

            final MatrixStore<Double> tmpXL = myLU.solve(input.getRHS());
            tmpX.fillMatching(tmpXL.builder().rows(0, (int) tmpQ.countColumns()).build());
            tmpL = tmpXL.builder().rows((int) tmpQ.countColumns(), (int) tmpXL.count()).build();
        }

        //        if (!tmpSolvable) {
        //            this.doValidate(input);
        //        }
        if (!tmpSolvable && (options.debug_appender != null)) {

            options.debug_appender.println("KKT system unsolvable");
            options.debug_appender.printmtrx("KKT", input.getKKT());
            options.debug_appender.printmtrx("RHS", input.getRHS());
            if (input.isConstrained()) {
                options.debug_appender.printmtrx("Q", input.getQ());
                options.debug_appender.printmtrx("C", input.getC());
                options.debug_appender.printmtrx("A", input.getA());
                options.debug_appender.printmtrx("B", input.getB());
            }
        }

        return new Output(tmpX, tmpL, tmpSolvable);
    }

    public boolean validate(final Input input) {

        try {

            this.doValidate(input);

            return true;

        } catch (final IllegalArgumentException exception) {

            return false;
        }
    }

    private void doValidate(final Input input) {

        final MatrixStore<Double> tmpQ = input.getQ();
        final MatrixStore<Double> tmpC = input.getC();
        final MatrixStore<Double> tmpA = input.getA();
        final MatrixStore<Double> tmpB = input.getB();

        if ((tmpQ == null) || (tmpC == null)) {
            throw new IllegalArgumentException("Neither Q nor C may be null!");
        }

        if (((tmpA != null) && (tmpB == null)) || ((tmpA == null) && (tmpB != null))) {
            throw new IllegalArgumentException("Either A or B is null, and the other one is not!");
        }

        myCholesky.compute(tmpQ, true);
        if (!myCholesky.isSPD()) {
            // Not positive definite. Check if at least positive semidefinite.

            final Eigenvalue<Double> tmpEvD = Eigenvalue.makePrimitive(true);

            tmpEvD.compute(tmpQ, true);

            final MatrixStore<Double> tmpD = tmpEvD.getD();

            tmpEvD.reset();

            final int tmpLength = (int) tmpD.countRows();
            for (int ij = 0; ij < tmpLength; ij++) {
                if (tmpD.doubleValue(ij, ij) < ZERO) {
                    throw new IllegalArgumentException("Q must be positive semidefinite!");
                }
            }
        }

        if (tmpA != null) {
            myLU.decompose(tmpA.countRows() < tmpA.countColumns() ? tmpA.transpose() : tmpA);
            if (myLU.getRank() != tmpA.countRows()) {
                throw new IllegalArgumentException("A must have full (row) rank!");
            }
        }
    }

}
