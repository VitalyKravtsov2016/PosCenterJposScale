@echo off

java -cp .;pcscale-1.14.9.jar;javapos-1.14.2.jar;log4j-api-2.25.1.jar;log4j-core-2.25.1.jar;jSerialComm-2.10.5.jar;xercesImpl-2.12.2.jar -Djava.library.path=. ru.poscenter.test.ConsoleTest
