JAR := target/eventb-generator-0.1.0-shaded.jar
PATTERN_DIR := node_Structure_2_xml
OUT_DIR := generated
PATTERN_FILES := $(wildcard $(PATTERN_DIR)/*.xml)
PROJECTS := $(patsubst $(PATTERN_DIR)/%.xml,%Proj,$(PATTERN_FILES))
PORT ?= 8080
WORKSPACE ?= $(OUT_DIR)
SRC_FILES := $(shell git ls-files "src")
ifeq ($(strip $(SRC_FILES)),)
  SRC_FILES := $(shell find src -type f)
endif

ifeq ($(OS),Windows_NT)
  DETECTED_OS := windows
  SHELL := cmd.exe
  SHELLFLAGS := /C
else
  DETECTED_OS := unix
  SHELL := /bin/sh
endif

define make_dir
$(if $(filter $(DETECTED_OS),windows),if not exist "$(1)" mkdir "$(1)",mkdir -p "$(1)")
endef

define rm_dir
$(if $(filter $(DETECTED_OS),windows),if exist "$(1)" rmdir /S /Q "$(1)",rm -rf "$(1)")
endef

.PHONY: generate regen clean-generated clean serve

build: $(JAR)

$(JAR): pom.xml $(SRC_FILES)
	mvn -q clean package

$(OUT_DIR)/%Proj: $(PATTERN_DIR)/%.xml build
	@$(call make_dir,$(OUT_DIR))
	java -jar $(JAR) -i $< -p $(notdir $@) -o $(OUT_DIR)

generate: $(addprefix $(OUT_DIR)/,$(PROJECTS))
	@echo "Generated $(PROJECTS)"

regen: clean-generated generate

clean-generated:
	@$(call rm_dir,$(OUT_DIR))

clean: clean-generated
	@$(call rm_dir,target)

serve: build
	@$(call make_dir,$(WORKSPACE))
	java -jar $(JAR) --server --port $(PORT) -o $(WORKSPACE)
