(ns harja.palvelin.tyokalut.interfact)

(defprotocol IEvent
  (lisaa-aihe [this aihe] [this aihe aihe-fn] "Lisää aiheen, jota voidaan kuunnella")
  (eventin-kuuntelija [this aihe event] "")
  (julkaise-event [this aihe nimi data] "Julkaise 'data' aiheeseeen 'nimi'"))
