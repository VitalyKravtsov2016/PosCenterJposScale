@echo off

java -cp .;pcscale-1.14.5.jar;javapos-1.14.2.jar;log4j-api-2.25.1.jar;log4j-core-2.25.1.jar;jSerialComm-2.10.5.jar;xercesImpl-2.12.2.jar -Djava.library.path=. ru.poscenter.jpostest.JposScaleTestApp
REM java -cp .;pcscale-1.14.5.jar;javapos-1.14.2.jar;log4j-api-2.25.1.jar;log4j-core-2.25.1.jar;jSerialComm-2.10.5.jar;xercesImpl-2.12.2.jar -Djava.library.path=. ru.poscenter.scaletst.ScaleTest
REM java -cp .;pcscale-1.14.5.jar;javapos-1.14.2.jar;log4j-api-2.25.1.jar;log4j-core-2.25.1.jar;jSerialComm-2.10.5.jar;xercesImpl-2.12.2.jar -Djava.library.path=. ru.poscenter.scalecalib.MainDialog
REM java -cp .;pcscale-1.14.5.jar;javapos-1.14.2.jar;log4j-api-2.25.1.jar;log4j-core-2.25.1.jar;jSerialComm-2.10.5.jar;xercesImpl-2.12.2.jar -Djava.library.path=. ru.poscenter.test.ConsoleTest
REM java -cp .;pcscale-1.14.5.jar;javapos-1.14.2.jar;log4j-api-2.25.1.jar;log4j-core-2.25.1.jar;jSerialComm-2.10.5.jar;xercesImpl-2.12.2.jar -Djava.library.path=. ru.poscenter.ScaleCLI