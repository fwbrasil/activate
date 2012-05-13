package net.fwbrasil.activate.util

object WildcardRegexUtil {

	def wildcardToRegex(wildcard: String) = {
		val s = new StringBuffer(wildcard.length)
		s.append('^')
		for (c <- wildcard)
			c match {
				case '*' =>
					s.append(".*")
				case '?' =>
					s.append(".")
				case '(' =>
				case ')' =>
				case '[' =>
				case ']' =>
				case '$' =>
				case '^' =>
				case '.' =>
				case '{' =>
				case '}' =>
				case '|' =>
				case '\\' =>
					s.append("\\")
					s.append(c)
				case default =>
					s.append(c)
			}
		s.append('$')
		s.toString
	}
	def matchesWildcard(string: String, wildcard: String) =
		wildcardToRegex(wildcard).matches(string)
}