@echo off
set msg=%*
if "%msg%"=="" set msg="Update chord parsing logic, add custom themes, fix Wear OS deployment, and clean up"

echo Staging changes...
git add .

echo Committing changes: %msg%
git commit -m %msg%

echo Pushing to remote...
git push

echo.
echo Done!
pause