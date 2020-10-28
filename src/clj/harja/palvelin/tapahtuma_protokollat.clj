(ns harja.palvelin.tapahtuma-protokollat)

(defprotocol Kuuntele
  (kuuntele! [this tapahtuma callback])
  (tarkkaile! [this tapahtuma] [this tapahtuma tyyppi]))

(defprotocol Julkaise
  (julkaise! [this tapahtuma payload host-name]))