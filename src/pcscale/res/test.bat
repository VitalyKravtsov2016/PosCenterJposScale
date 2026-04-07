@echo off

java -cp .;pcscale-1.13.1.jar;javapos-1.14.2.jar;log4j-api-2.25.1.jar;log4j-core-2.25.1.jar;nrjavaserial-3.9.3.jar;xercesImpl-2.12.2.jar -Djava.library.path=. ru.poscenter.ScaleTests
REM java -cp .;pcscale-1.13.1.jar;javapos-1.14.2.jar;log4j-api-2.25.1.jar;log4j-core-2.25.1.jar;nrjavaserial-3.9.3.jar;xercesImpl-2.12.2.jar -Djava.library.path=. ru.poscenter.scaletst.ScaleTest
REM java -cp .;pcscale-1.13.1.jar;javapos-1.14.2.jar;log4j-api-2.25.1.jar;log4j-core-2.25.1.jar;nrjavaserial-3.9.3.jar;xercesImpl-2.12.2.jar -Djava.library.path=. ru.poscenter.scalecalib.MainDialog
REM java -cp .;pcscale-1.13.1.jar;javapos-1.14.2.jar;log4j-api-2.25.1.jar;log4j-core-2.25.1.jar;nrjavaserial-3.9.3.jar;xercesImpl-2.12.2.jar -Djava.library.path=. ru.poscenter.test.ConsoleTest
REM java -cp .;pcscale-1.13.1.jar;javapos-1.14.2.jar;log4j-api-2.25.1.jar;log4j-core-2.25.1.jar;nrjavaserial-3.9.3.jar;xercesImpl-2.12.2.jar -Djava.library.path=. ru.poscenter.ScaleCLI