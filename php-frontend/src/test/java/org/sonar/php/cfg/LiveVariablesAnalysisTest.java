/*
 * SonarQube PHP Plugin
 * Copyright (C) 2010-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.php.cfg;

import org.junit.Test;
import org.sonar.php.PHPTreeModelTest;
import org.sonar.php.parser.PHPLexicalGrammar;
import org.sonar.php.tree.symbols.SymbolTableImpl;
import org.sonar.plugins.php.api.tree.CompilationUnitTree;

import static org.assertj.core.api.Assertions.assertThat;

public class LiveVariablesAnalysisTest extends PHPTreeModelTest {

  @Test
  public void test() {
    String body = "" +
      "foo();" +
      "if (a) {" +
      "  $x = 1;" +
      "}";

    CompilationUnitTree cut = parse("<?php function f() { " + body + " }", PHPLexicalGrammar.COMPILATION_UNIT);
    SymbolTableImpl symbolTable = SymbolTableImpl.create(cut);
    ControlFlowGraph cfg = ControlFlowGraph.build(cut.script());
    LiveVariablesAnalysis analysis = LiveVariablesAnalysis.analyze(cfg, symbolTable);
    assertThat(analysis).isNotNull();
  }

}
