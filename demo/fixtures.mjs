export const fixtures = {
  "exceptions": {
    "ledger": "transaction_id,currency,amount,description\ntxn-001,CAD,129.99,\"Blue Harbor Books, order 1042\"\ntxn-002,CAD,48.50,Westbrook subscription\ntxn-003,CAD,-20.00,Partial refund\ntxn-004,USD,75.00,Seaside workshop\ntxn-005,CAD,14.25,Pending settlement\ntxn-006,CAD,32.00,Duplicated export row\ntxn-006,CAD,32.00,Duplicated export row\ntxn-008,JPY,1500,\"Quoted \"\"special\"\" item\"\n",
    "processor": "transaction_id,currency,amount,description\ntxn-008,JPY,1500,Japanese yen purchase\ntxn-001,CAD,129.990,Books payment\ntxn-002,CAD,47.50,Unexpected adjustment\ntxn-003,CAD,-20.0,Partial refund\ntxn-004,CAD,75.00,Incorrect currency\ntxn-006,CAD,32.00,Single settlement\ntxn-007,CAD,9.99,Unposted payment\n"
  },
  "matched": {
    "ledger": "transaction_id,currency,amount,description\npurchase-01,CAD,0.30,Exact decimal example\nrefund-01,USD,-12.50,Refund\nyen-01,JPY,1800,No fractional minor unit\ndinar-01,KWD,4.125,Three fractional minor units\n",
    "processor": "transaction_id,currency,amount,description\nyen-01,JPY,1800.000,Equivalent amount\ndinar-01,KWD,4.125,Equivalent amount\nrefund-01,USD,-12.500,Equivalent amount\npurchase-01,CAD,0.300,Equivalent amount\n"
  },
  "precision": {
    "ledger": "transaction_id,currency,amount,description\nlarge,CAD,999999999999999999999999999999.99,Exact large amount\nrefund,KWD,-0.125,Refund\nyen,JPY,1500,Yen\n",
    "processor": "transaction_id,currency,amount,description\nyen,JPY,1500.000,Same amount\nlarge,CAD,999999999999999999999999999999.98,One cent short\nrefund,KWD,-0.125,Refund\n"
  }
};
