package de.lolhens.cdncache

import org.scalajs.dom

object Main {
  def main(args: Array[String]): Unit = {
    MainComponent.Component(MainComponent.Props())
      .renderIntoDOM(dom.document.getElementById("root"))
  }
}
