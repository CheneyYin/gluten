find cpp/core -regex '.*\.\(cc\|hpp\|cu\|c\|h\)' -exec clang-format-11 -style=file -i {} \;
find cpp/velox -regex '.*\.\(cc\|hpp\|cu\|c\|h\)' -exec clang-format-11 -style=file -i {} \;
