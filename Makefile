JAR := target/eventb-generator-0.1.0-shaded.jar
PATTERN_DIR := node_Structure
OUT_DIR := generated
PATTERN_FILES := $(wildcard $(PATTERN_DIR)/*.xml)
PROJECTS := $(patsubst $(PATTERN_DIR)/%.xml,%Proj,$(PATTERN_FILES))

.PHONY: generate regen clean-generated clean

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
