package eu.jjst

package object models {
  case class Coords(x: Int, y: Int)
  case class Move(outerBoardCoords: Coords, innerBoardCoords: Coords)
}
