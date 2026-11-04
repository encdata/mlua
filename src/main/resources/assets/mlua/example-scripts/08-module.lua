-- A reusable module. Require it with: local format = Require("08-module")
return function(name)
    return "[Example] " .. tostring(name)
end
