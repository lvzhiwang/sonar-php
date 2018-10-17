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

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.sonar.plugins.php.api.tree.ScriptTree;
import org.sonar.plugins.php.api.tree.Tree;
import org.sonar.plugins.php.api.tree.declaration.FunctionDeclarationTree;
import org.sonar.plugins.php.api.tree.declaration.MethodDeclarationTree;
import org.sonar.plugins.php.api.tree.expression.FunctionExpressionTree;
import org.sonar.plugins.php.api.tree.statement.BlockTree;

import static org.sonar.php.tree.TreeUtils.findParentWithKind;

/**
 * The <a href="https://en.wikipedia.org/wiki/Control_flow_graph">Control Flow Graph</a>
 * for a PHP script or for the body of a function.
 *
 * <p>Each node of the graph represents a list of elements which are executed sequentially.
 * Each node has:
 * <ul>
 * <li>one ore more successor blocks,</li>
 * <li>zero or more predecessor blocks.</li>
 * </ul>
 * </p>
 * <p>
 * A Control Flow Graph has a single start node and a single end node.
 * The end node has no successor and no element.
 */
public class ControlFlowGraph {

  private final CfgBlock start;
  private final PhpCfgEndBlock end;
  private final Set<CfgBlock> blocks;

  ControlFlowGraph(ImmutableSet<CfgBlock> blocks, CfgBlock start, PhpCfgEndBlock end) {
    this.start = start;
    this.end = end;
    this.blocks = blocks;
  }

  public static ControlFlowGraph build(BlockTree body) {
    return new ControlFlowGraphBuilder(body.statements()).getGraph();
  }

  public static ControlFlowGraph build(ScriptTree scriptTree) {
    return new ControlFlowGraphBuilder(scriptTree.statements()).getGraph();
  }

  @CheckForNull
  public static ControlFlowGraph findCFG(Tree tree) {
    Tree treeWithFlow = findParentWithKind(tree,
      Tree.Kind.FUNCTION_DECLARATION,
      Tree.Kind.FUNCTION_EXPRESSION,
      Tree.Kind.METHOD_DECLARATION,
      Tree.Kind.SCRIPT);
    if (treeWithFlow == null) {
      return null;
    }
    switch (treeWithFlow.getKind()) {
      case FUNCTION_DECLARATION:
        return build(((FunctionDeclarationTree) treeWithFlow).body());
      case FUNCTION_EXPRESSION:
        return build(((FunctionExpressionTree) treeWithFlow).body());
      case METHOD_DECLARATION:
        Tree body = ((MethodDeclarationTree) treeWithFlow).body();
        if (body.is(Tree.Kind.BLOCK)) {
          return build(((BlockTree) body));
        } else {
          return null;
        }
      case SCRIPT:
        return build(((ScriptTree) treeWithFlow));
      default:
        return null;
    }
  }

  public CfgBlock start() {
    return start;
  }

  public CfgBlock end() {
    return end;
  }

  /**
   * Includes start and end blocks
   */
  public Set<CfgBlock> blocks() {
    return blocks;
  }
}
