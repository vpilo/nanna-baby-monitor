package org.vpilo.babymonitor.errorreport.data

/**
 * The deaths of this app's processes recorded by the OS, most recent first.
 * Empty where the OS records none.
 */
internal expect fun readProcessExitRecords(): List<ProcessExitRecord>
