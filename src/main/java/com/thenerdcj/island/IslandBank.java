package com.thenerdcj.island;

import com.thenerdcj.database.GridPosition;

/**
 * Island Bank - Shared island funds
 */
public class IslandBank {

    private final GridPosition gridPosition;
    private double balance;

    public IslandBank(GridPosition gridPosition) {
        this.gridPosition = gridPosition;
        this.balance = 0.0;
    }

    public GridPosition getGridPosition() { return gridPosition; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = Math.max(0, balance); }

    public void deposit(double amount) {
        if (amount > 0) this.balance += amount;
    }

    public boolean withdraw(double amount) {
        if (amount > 0 && this.balance >= amount) {
            this.balance -= amount;
            return true;
        }
        return false;
    }
}
