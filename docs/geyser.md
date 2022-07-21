# Proposed design:

- Validator 1 serum-validator-va will be side-by-side with another massive Bare Metal server in OVH Vint Hill, 
running Postgres, called serum-db-va .
- Geyser plugin on Validator 1: Postgres plugin written by Solana Labs, pointed to serum-db-va.

## serum-db-va
  - Massive Postgres server in same DC as Validator (OVH Vint Hill), w/ private networking. 
  - At least 128/256GB of RAM, High CPU, high private bandwidth
  - Incredibly high hard drive space, minimum 4TB SSD, pref 4B SSD + extra HDD storage (to be used for less "hot" data).
    
## serum-validator-va (aka Validator 1, already up and running.)
- Geyser config:
  - Private neworking pointed to: serum-db-va 
  - Accounts Owner Selector w/ only Serum v3 Program ID.
  - ```json
    "accounts_selector": {"owners" : [SERUM_V3]}
    ```
  - Postgres tables will store all Serum program account data, accounts such as: bid/ask order book, event queue 
  (trade history), market struct, etc.

