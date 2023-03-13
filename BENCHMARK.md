Android - Pixel 4a - ef67e9a
```
Test CommonTests.BenchmarkTest.benchmark 01 cold 100: 
    insert: 103 ms (1.03801937 ms/op)
    update: 68 ms (0.6895458999999999 ms/op)
    update chunked: 11 ms (0.11988386999999999 ms/op)
    select all in 10 chunks: 86 ms (0.086494592 ms/op)
    select all cursor: 5 ms (0.053124489999999996 ms/op)
    select one getFirst: 98 ms (0.98874489 ms/op)
    select more getFirst: 108 ms (1.08665376 ms/op)
    select cursor first: 122 ms (1.22697929 ms/op)
    delete: 107 ms (1.0721891700000001 ms/op)

Test CommonTests.BenchmarkTest.benchmark 02 warm 1000: 
    insert: 840 ms (0.84038274 ms/op)
    update: 960 ms (0.960992179 ms/op)
    update chunked: 116 ms (0.116583241 ms/op)
    select all in 10 chunks: 893 ms (0.08934949319999999 ms/op)
    select all cursor: 33 ms (0.033522503 ms/op)
    select one getFirst: 1179 ms (1.179184337 ms/op)
    select more getFirst: 1179 ms (1.179002514 ms/op)
    select cursor first: 916 ms (0.916265508 ms/op)
    delete: 1360 ms (1.3604352389999999 ms/op)

Test CommonTests.BenchmarkTest.benchmark 03 hot 10 000: 
    insert: 8938 ms (0.8938165057 ms/op)
    update: 8632 ms (0.8632454036999999 ms/op)
    update chunked: 1208 ms (0.12082320469999999 ms/op)
    select all in 10 chunks: 9045 ms (0.09045651733999999 ms/op)
    select all cursor: 328 ms (0.0328773366 ms/op)
    select one getFirst: 11634 ms (1.1634117149 ms/op)
    select more getFirst: 10585 ms (1.0585540638 ms/op)
    select cursor first: 8750 ms (0.8750820664000001 ms/op)
    delete: 9565 ms (0.9565469599 ms/op)
```

Android - Pixel 4a - ef67e9a with statement pooling
```
Test CommonTests.BenchmarkTest.benchmark 01 cold 100: 
    insert: 101 ms (1.01844958 ms/op)
    update: 52 ms (0.5288370299999999 ms/op)
    update chunked: 9 ms (0.09967292999999999 ms/op)
    select all in 10 chunks: 72 ms (0.072713601 ms/op)
    select all cursor: 5 ms (0.05470365 ms/op)
    select one getFirst: 92 ms (0.92533186 ms/op)
    select more getFirst: 92 ms (0.92763395 ms/op)
    select cursor first: 81 ms (0.8187204 ms/op)
    delete: 97 ms (0.9721912399999999 ms/op)

Test CommonTests.BenchmarkTest.benchmark 02 warm 1000: 
    insert: 699 ms (0.699146736 ms/op)
    update: 797 ms (0.797218986 ms/op)
    update chunked: 113 ms (0.113425064 ms/op)
    select all in 10 chunks: 767 ms (0.07678316389999999 ms/op)
    select all cursor: 33 ms (0.033355212 ms/op)
    select one getFirst: 1028 ms (1.02861328 ms/op)
    select more getFirst: 948 ms (0.948315563 ms/op)
    select cursor first: 830 ms (0.830956645 ms/op)
    delete: 1071 ms (1.071085732 ms/op)

Test CommonTests.BenchmarkTest.benchmark 03 hot 10 000: 
    insert: 7622 ms (0.762277529 ms/op)
    update: 7575 ms (0.7575037265 ms/op)
    update chunked: 982 ms (0.0982721869 ms/op)
    select all in 10 chunks: 7839 ms (0.07839549531 ms/op)
    select all cursor: 324 ms (0.032464086600000004 ms/op)
    select one getFirst: 9859 ms (0.9859394158999999 ms/op)
    select more getFirst: 9379 ms (0.9379255153 ms/op)
    select cursor first: 7472 ms (0.7472971421 ms/op)
    delete: 10717 ms (1.0717163256 ms/op)
```

Chromium - AMD Ryzen 9 5900HS - ef67e9a
```
Test CommonTests.BenchmarkTest.benchmark 01 cold 100: 
    insert: 79 ms (0.792 ms/op)
    update: 38 ms (0.38799999999999996 ms/op)
    update chunked: 15 ms (0.159 ms/op)
    select all in 10 chunks: 54 ms (0.0545 ms/op)
    select all cursor: 8 ms (0.087 ms/op)
    select one getFirst: 30 ms (0.3 ms/op)
    select more getFirst: 28 ms (0.281 ms/op)
    select cursor first: 43 ms (0.433 ms/op)
    delete: 44 ms (0.446 ms/op)

Test CommonTests.BenchmarkTest.benchmark 02 warm 1000: 
    insert: 427 ms (0.4275 ms/op)
    update: 379 ms (0.3798 ms/op)
    update chunked: 145 ms (0.1459 ms/op)
    select all in 10 chunks: 353 ms (0.03532 ms/op)
    select all cursor: 38 ms (0.0385 ms/op)
    select one getFirst: 221 ms (0.22169999999999998 ms/op)
    select more getFirst: 226 ms (0.2265 ms/op)
    select cursor first: 314 ms (0.3147 ms/op)
    delete: 255 ms (0.2552 ms/op)

Test CommonTests.BenchmarkTest.benchmark 03 hot 10 000: 
    insert: 3323 ms (0.33233 ms/op)
    update: 3369 ms (0.33697 ms/op)
    update chunked: 2552 ms (0.25521 ms/op)
    select all in 10 chunks: 4846 ms (0.048466999999999996 ms/op)
    select all cursor: 963 ms (0.09637000000000001 ms/op)
    select one getFirst: 3558 ms (0.35588000000000003 ms/op)
    select more getFirst: 3812 ms (0.38125 ms/op)
    select cursor first: 6166 ms (0.61666 ms/op)
    delete: 4115 ms (0.41156000000000004 ms/op)
```

Edge - AMD Ryzen 9 5900HS - ef67e9a
```
Test CommonTests.BenchmarkTest.benchmark 01 cold 100: 
    insert: 77 ms (0.779 ms/op)
    update: 45 ms (0.456 ms/op)
    update chunked: 18 ms (0.184 ms/op)
    select all in 10 chunks: 71 ms (0.0716 ms/op)
    select all cursor: 10 ms (0.102 ms/op)
    select one getFirst: 32 ms (0.325 ms/op)
    select more getFirst: 38 ms (0.381 ms/op)
    select cursor first: 49 ms (0.498 ms/op)
    delete: 35 ms (0.359 ms/op)
Test CommonTests.BenchmarkTest.benchmark 02 warm 1000: 
    insert: 474 ms (0.4747 ms/op)
    update: 414 ms (0.4147 ms/op)
    update chunked: 185 ms (0.185 ms/op)
    select all in 10 chunks: 415 ms (0.041569999999999996 ms/op)
    select all cursor: 33 ms (0.033600000000000005 ms/op)
    select one getFirst: 217 ms (0.217 ms/op)
    select more getFirst: 235 ms (0.2351 ms/op)
    select cursor first: 297 ms (0.297 ms/op)
    delete: 256 ms (0.25689999999999996 ms/op)
Test CommonTests.BenchmarkTest.benchmark 03 hot 10 000: 
    insert: 3263 ms (0.32635 ms/op)
    update: 3532 ms (0.35323000000000004 ms/op)
    update chunked: 2071 ms (0.20711 ms/op)
    select all in 10 chunks: 3427 ms (0.034274 ms/op)
    select all cursor: 270 ms (0.02707 ms/op)
    select one getFirst: 2095 ms (0.20958000000000002 ms/op)
    select more getFirst: 2351 ms (0.2351 ms/op)
    select cursor first: 3277 ms (0.32774000000000003 ms/op)
    delete: 3007 ms (0.30073 ms/op)
```

Firefox - AMD Ryzen 9 5900HS - ef67e9a
```
Test CommonTests.BenchmarkTest.benchmark 01 cold 100:
    insert: 71 ms (0.71 ms/op)
    update: 73 ms (0.73 ms/op)
    update chunked: 24 ms (0.24 ms/op)
    select all in 10 chunks: 130 ms (0.13 ms/op)
    select all cursor: 18 ms (0.18 ms/op)
    select one getFirst: 45 ms (0.45 ms/op)
    select more getFirst: 46 ms (0.46 ms/op)
    select cursor first: 85 ms (0.85 ms/op)
    delete: 54 ms (0.54 ms/op)

Test CommonTests.BenchmarkTest.benchmark 02 warm 1000:
    insert: 473 ms (0.473 ms/op)
    update: 387 ms (0.387 ms/op)
    update chunked: 161 ms (0.161 ms/op)
    select all in 10 chunks: 411 ms (0.0411 ms/op)
    select all cursor: 52 ms (0.052 ms/op)
    select one getFirst: 254 ms (0.254 ms/op)
    select more getFirst: 270 ms (0.27 ms/op)
    select cursor first: 410 ms (0.41 ms/op)
    delete: 290 ms (0.29 ms/op)

Test CommonTests.BenchmarkTest.benchmark 03 hot 10 000:
    insert: 3858 ms (0.3858 ms/op)
    update: 5046 ms (0.5046 ms/op)
    update chunked: 3499 ms (0.3499 ms/op)
    select all in 10 chunks: 6445 ms (0.06445 ms/op)
    select all cursor: 599 ms (0.0599 ms/op)
    select one getFirst: 4503 ms (0.4503 ms/op)
    select more getFirst: 2342 ms (0.2342 ms/op)
    select cursor first: 6183 ms (0.6183 ms/op)
    delete: 4310 ms (0.431 ms/op)
```

Firefox Developer - AMD Ryzen 9 5900HS - ef67e9a
```
Test CommonTests.BenchmarkTest.benchmark 01 cold 100: 
    insert: 43 ms (0.43 ms/op)
    update: 39 ms (0.39 ms/op)
    update chunked: 16 ms (0.16 ms/op)
    select all in 10 chunks: 55 ms (0.055 ms/op)
    select all cursor: 12 ms (0.12 ms/op)
    select one getFirst: 26 ms (0.26 ms/op)
    select more getFirst: 26 ms (0.26 ms/op)
    select cursor first: 41 ms (0.41 ms/op)
    delete: 28 ms (0.28 ms/op)

Test CommonTests.BenchmarkTest.benchmark 02 warm 1000:
    insert: 309 ms (0.309 ms/op)
    update: 295 ms (0.295 ms/op)
    update chunked: 122 ms (0.122 ms/op)
    select all in 10 chunks: 339 ms (0.0339 ms/op)
    select all cursor: 51 ms (0.051 ms/op)
    select one getFirst: 205 ms (0.205 ms/op)
    select more getFirst: 208 ms (0.208 ms/op)
    select cursor first: 355 ms (0.355 ms/op)
    delete: 218 ms (0.218 ms/op)

Test CommonTests.BenchmarkTest.benchmark 03 hot 10 000:
    insert: 4588 ms (0.4588 ms/op)
    update: 3199 ms (0.3199 ms/op)
    update chunked: 2995 ms (0.2995 ms/op)
    select all in 10 chunks: 5610 ms (0.0561 ms/op)
    select all cursor: 554 ms (0.0554 ms/op)
    select one getFirst: 2266 ms (0.2266 ms/op)
    select more getFirst: 4158 ms (0.4158 ms/op)
    select cursor first: 5585 ms (0.5585 ms/op)
    delete: 2336 ms (0.2336 ms/op)
```