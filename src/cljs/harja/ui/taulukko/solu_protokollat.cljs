(ns harja.ui.taulukko.solu-protokollat)

(defprotocol IFmt
  (-lisaa-fmt [this f] "Lisää asiaan formatointi funktion")
  (-lisaa-fmt-aktiiviselle [this f] "Jos osa on aktiivinen, minkälainen formatointi?"))

(defprotocol ISolu)

(defn lisaa-fmt [solu f]
  {:pre [(satisfies? IFmt solu)
         (fn? f)]}
  (-lisaa-fmt solu f))
(defn lisaa-fmt-aktiiviselle [solu f]
  {:pre [(satisfies? IFmt solu)
         (fn? f)]}
  (-lisaa-fmt-aktiiviselle solu f))
