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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.sonar.plugins.php.api.symbols.Symbol;
import org.sonar.plugins.php.api.symbols.SymbolTable;
import org.sonar.plugins.php.api.tree.Tree;
import org.sonar.plugins.php.api.tree.expression.AssignmentExpressionTree;
import org.sonar.plugins.php.api.tree.expression.ExpressionTree;
import org.sonar.plugins.php.api.tree.expression.FunctionCallTree;

public class LiveVariablesAnalysis {

  private final ControlFlowGraph controlFlowGraph;
  private final Map<CfgBlock, LiveVariables> liveVariablesPerBlock;

  private LiveVariablesAnalysis(ControlFlowGraph cfg, SymbolTable symbols) {
    controlFlowGraph = cfg;
    liveVariablesPerBlock = compute(controlFlowGraph, symbols);
  }

  public LiveVariables getLiveVariables(CfgBlock block) {
    return liveVariablesPerBlock.get(block);
  }

  public static LiveVariablesAnalysis analyze(ControlFlowGraph cfg, SymbolTable symbols) {
    return new LiveVariablesAnalysis(cfg, symbols);
  }

  private static Map<CfgBlock, LiveVariables> compute(ControlFlowGraph cfg, SymbolTable symbols) {
    Map<CfgBlock, LiveVariables> liveVariablesPerBlock = new HashMap<>();
    cfg.blocks().forEach(block -> {
      LiveVariables liveVariables = new LiveVariables(block, symbols);
      liveVariablesPerBlock.put(block, liveVariables);
    });

    Deque<CfgBlock> worklist = new ArrayDeque<>(cfg.blocks());
    while (!worklist.isEmpty()) {
      CfgBlock block = worklist.pop();
      LiveVariables liveVariables = liveVariablesPerBlock.get(block);
      boolean changes = liveVariables.propagate(liveVariablesPerBlock);
      if (changes) {
        block.predecessors().forEach(worklist::push);
      }
    }

    return liveVariablesPerBlock;
  }

  private static class LiveVariables {
    private final CfgBlock block;
    private final SymbolTable symbols;
    // 'gen' or 'use' - variables that are being read in the block
    private final Set<Symbol> gen = new HashSet<>();
    // 'kill' or 'def' - variables that are being written before being read in the block
    private final Set<Symbol> kill = new HashSet<>();
    // the 'in' and 'out' change during the algorithm
    private Set<Symbol> in = new HashSet<>();
    private Set<Symbol> out = new HashSet<>();

    LiveVariables(CfgBlock block, SymbolTable symbols) {
      this.block = block;
      this.symbols = symbols;
      initialize();
    }

    boolean propagate(Map<CfgBlock, LiveVariables> liveVariablesPerBlock) {
      // propagate the out values backwards, from successors
      out.clear();
      block.successors().stream().map(liveVariablesPerBlock::get).map(df -> df.in).forEach(out::addAll);
      // in = union + (out - kill)
      Set<Symbol> newIn = new HashSet<>(gen);
      newIn.addAll(Sets.difference(out, kill));
      boolean inHasChanged = !newIn.equals(in);
      in = newIn;
      return inHasChanged;
    }

    private void initialize() {
      // process elements from bottom to top
      Set<Tree> assignmentLHS = new HashSet<>();
      for (Tree element : Lists.reverse(block.elements())) {
        Symbol symbol = null;
        switch (element.getKind()) {
          case ASSIGNMENT:
            ExpressionTree lhs = ((AssignmentExpressionTree)element).variable();
            if (lhs.is(Tree.Kind.NAME_IDENTIFIER)) {
              symbol = symbols.getSymbol(lhs);
            }
            if (symbol != null) {
              assignmentLHS.add(lhs);
              kill.add(symbol);
              gen.remove(symbol);
            }
            break;
          case NAME_IDENTIFIER:
            symbol = symbols.getSymbol(element);
            if (symbol != null) {
              gen.add(symbol);
            }
            break;
          case VARIABLE_IDENTIFIER:
            symbol = symbols.getSymbol(element);
            if (symbol != null) {
              // is local?
              kill.add(symbol);
              gen.remove(symbol);
            }
            break;
          case FUNCTION_CALL:
            // if arguments are identifiers, add to gen
            FunctionCallTree functionCallTree = (FunctionCallTree) element;
            functionCallTree.arguments().forEach(arg -> {
              if (arg.is(Tree.Kind.NAME_IDENTIFIER)) {
                Symbol s = symbols.getSymbol(arg);
                if (s !=null) {
                  gen.add(s);
                }
              }
            });
            break;
          case ARRAY_INITIALIZER_FUNCTION:
            // if arguments are identifiers, add to gen
          case ARRAY_INITIALIZER_BRACKET:
            // if arguments are identifiers, add to gen
          case NEW_EXPRESSION:
            // if arguments are identifiers, add to gen
          default:
            // ignore
        }
      }
    }

  }
}
