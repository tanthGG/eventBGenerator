JAR := target/eventb-generator-0.1.0-shaded.jar
PATTERN_DIR := node_Structure
OUT_DIR := generated
PATTERN_FILES := $(wildcard $(PATTERN_DIR)/*.xml)
PROJECTS := $(patsubst $(PATTERN_DIR)/%.xml,%Proj,$(PATTERN_FILES))
PORT ?= 8080
WORKSPACE ?= $(OUT_DIR)

.PHONY: generate regen clean-generated clean serve

build: $(JAR)

$(JAR): pom.xml $(shell find src -type f)
	mvn -q clean package

$(OUT_DIR)/%Proj: $(PATTERN_DIR)/%.xml build
	@mkdir -p $(OUT_DIR)
	java -jar $(JAR) -i $< -p $(notdir $@) -o $(OUT_DIR)

generate: $(addprefix $(OUT_DIR)/,$(PROJECTS))
	@echo "Generated $(PROJECTS)"

regen: clean-generated generate

clean-generated:
	rm -rf $(OUT_DIR)

clean: clean-generated
	rm -rf target

serve: build
	@mkdir -p $(WORKSPACE)
	java -jar $(JAR) --server --port $(PORT) -o $(WORKSPACE)
